# 🎯 Focus Architect - Complete Guide

**Version**: 1.0
**Status**: Production Ready ✅
**Last Updated**: 2025-10-03

---

## 📖 Table of Contents

1. [What is Focus Architect?](#what-is-focus-architect)
2. [Quick Start](#quick-start)
3. [Usage Examples](#usage-examples)
4. [Files Structure](#files-structure)
5. [Automated Tools](#automated-tools)
6. [Integration with tv-agent](#integration-with-tv-agent)
7. [Common Workflows](#common-workflows)
8. [Troubleshooting](#troubleshooting)

---

## What is Focus Architect?

**Focus Architect** is a specialized subagent for managing Android TV focus and navigation systems in the TV_componenty project. It provides:

- ✅ **Conflict Prevention**: Detects key event conflicts before they happen
- 📋 **Code Review**: Validates navigation implementations
- 🔍 **System Audits**: Scans entire project for issues
- 📚 **Documentation**: Maintains focus system documentation
- 🛠️ **Automated Tools**: Scripts for quick checks and full audits

### When to Use Focus Architect:

- Adding new navigation sections
- Modifying existing key event handlers
- Fixing focus-related bugs
- Code review of focus changes
- System-wide focus audits

---

## Quick Start

### 1. Install (One-Time Setup)

All files are already created in your project:

```bash
cd /Users/uxellenceuxe/TV_componenty

# Verify installation
ls -la docs/agents/focus-architect.md
ls -la docs/focus-patterns/
ls -la scripts/focus-*.sh

# Scripts should be executable
./scripts/focus-audit.sh --help 2>/dev/null || echo "Scripts ready!"
```

### 2. Run Your First Audit

```bash
# Full system audit (takes 30 seconds)
./scripts/focus-audit.sh

# Quick pre-commit check (takes 5 seconds)
./scripts/focus-quick-check.sh
```

### 3. Read the Documentation

```bash
# Core agent definition
open docs/agents/focus-architect.md

# Common conflicts reference
open docs/focus-patterns/common-conflicts.md

# Resolution strategies
open docs/focus-patterns/resolution-strategies.md
```

---

## Usage Examples

### Example 1: Pre-Implementation Audit

**Scenario**: You need to add a new "SPORT" section with multi-row navigation.

```bash
# Step 1: Run Focus Architect for analysis
claude code \
  -f docs/agents/focus-architect.md \
  -f FOCUS_NAVIGATION_GUIDE.md \
  -f KEY_EVENT_CHECKLIST.md \
  -f docs/focus-patterns/delegation-patterns.md \
  -p "Focus Architect: Pre-implementation audit for new section SPORT.
      Requirements: 3 categories (Live, Highlights, Schedule), multi-row navigation.
      Use Template 1 (Pre-Implementation Report)."

# Step 2: Review the report
# Focus Architect will provide:
# - Conflict analysis
# - Recommended pattern (likely: Pattern 1 - Full Delegation)
# - Implementation plan
# - Files to create/modify

# Step 3: Implement based on recommendations
# Step 4: Request code review (see Example 2)
```

### Example 2: Code Review

**Scenario**: You've implemented SportSection.kt and want validation.

```bash
# Run code review
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/components/SportSection.kt \
  -f app/src/main/java/com/example/tv/TopMenuScreen2.kt \
  -p "Focus Architect: Code review of SportSection navigation.
      Check delegation, callbacks, and integration with TopMenuScreen2.
      Use Template 2 (Code Review Report)."

# Focus Architect will verify:
# - Proper delegation pattern
# - onReturnToMenu callback present and invoked
# - No conflicts with existing handlers
# - Naming conventions followed
# - Documentation updated
```

### Example 3: Fixing a Bug

**Scenario**: User reports "Can't navigate in VOD section after update."

```bash
# Step 1: Quick diagnosis with automated tools
./scripts/focus-audit.sh | grep "VOD"

# Step 2: If conflict found, get resolution strategy
claude code \
  -f docs/agents/focus-architect.md \
  -f docs/focus-patterns/common-conflicts.md \
  -f docs/focus-patterns/resolution-strategies.md \
  -f app/src/main/java/com/example/tv/TopMenuScreen2.kt \
  -f app/src/main/java/com/example/tv/components/VodWithChannels.kt \
  -p "Focus Architect: VOD navigation broken. User can't move between channels.
      Analyze TopMenuScreen2 and VodWithChannels for conflicts.
      Provide step-by-step resolution."

# Step 3: Apply fix based on recommendations
# Step 4: Verify with audit
./scripts/focus-audit.sh
```

### Example 4: System-Wide Audit

**Scenario**: Before release, you want to ensure zero conflicts.

```bash
# Step 1: Run automated audit
./scripts/focus-audit.sh > focus-audit-report.txt

# Step 2: If issues found, run deep analysis
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/*.kt \
  -p "Focus Architect: Full system conflict detection.
      Analyze all key handlers across entire project.
      Use Template 3 (Conflict Detection Report)."

# Step 3: Review report and prioritize fixes
# Step 4: Fix each issue using resolution strategies
# Step 5: Re-run audit until clean
./scripts/focus-audit.sh
```

---

## Files Structure

```
TV_componenty/
├── docs/
│   ├── agents/
│   │   └── focus-architect/
│   │       ├── focus-architect.md         # Main agent definition
│   │       └── README.md                  # This file
│   └── focus-patterns/
│       ├── delegation-patterns.md         # Proven patterns reference
│       ├── common-conflicts.md            # Known issues + solutions
│       └── resolution-strategies.md       # Step-by-step fixes
└── scripts/
    ├── focus-audit.sh                     # Full system audit
    └── focus-quick-check.sh               # Fast pre-commit check
```

### File Purposes:

| File | Purpose | When to Use |
|------|---------|-------------|
| `focus-architect.md` | Core agent definition, templates, workflows | Every Focus Architect invocation |
| `delegation-patterns.md` | Pattern reference (Pattern 1, 2, 3) | Choosing implementation approach |
| `common-conflicts.md` | Catalog of known conflicts + solutions | Recognizing familiar issues |
| `resolution-strategies.md` | Step-by-step fix procedures | Fixing detected conflicts |
| `focus-audit.sh` | Automated full system scan | Before commits, before releases |
| `focus-quick-check.sh` | Fast validation | Git pre-commit hook |

---

## Automated Tools

### Tool 1: focus-audit.sh (Full Audit)

**Purpose**: Comprehensive system-wide focus conflict detection

**Runtime**: ~30 seconds

**What it checks**:
- Key handler inventory (Back, Up, Down, Left, Right)
- TopMenuScreen2 delegation configuration
- onReturnToMenu callback presence
- Event type verification coverage
- Hardcoded state manipulation
- Potential conflicts (multiple handlers per file)

**Usage**:
```bash
# Run full audit
./scripts/focus-audit.sh

# Save report to file
./scripts/focus-audit.sh > audit-report-$(date +%Y%m%d).txt

# Run before major changes
git stash
./scripts/focus-audit.sh  # Baseline
git stash pop
# Make changes
./scripts/focus-audit.sh  # Compare
```

**Exit codes**:
- `0`: All checks passed (green)
- `0`: Warnings found (yellow - still passes)

**Output example**:
```
🔍 Focus System Audit
====================

📋 Scanning for key handlers...
Key.Back: 35 handlers
Key.DirectionUp: 85 handlers

📐 Checking TopMenuScreen2 delegation...
✅ VOD: Properly delegated
✅ MOJE: Properly delegated

🔄 Checking onReturnToMenu callbacks...
Found 279 callback references
✅ Callbacks present in sections

...
```

### Tool 2: focus-quick-check.sh (Pre-Commit Check)

**Purpose**: Fast validation before git commit

**Runtime**: ~5 seconds

**What it checks**:
- VOD delegation (most common regression)
- Hardcoded state manipulation
- Event type checks in modified files
- Callback presence

**Usage**:
```bash
# Run manually before commit
./scripts/focus-quick-check.sh
git commit -m "..."

# Add as git pre-commit hook
ln -s ../../scripts/focus-quick-check.sh .git/hooks/pre-commit
# Now runs automatically on every commit
```

**Exit codes**:
- `0`: Safe to commit
- `1`: Issues found, fix before committing

**Output example**:
```
⚡ Focus Quick Check

Checking VOD delegation... OK
Checking hardcoded state... OK
Checking event type checks... WARN
  ⚠️  Some handlers may be missing event type checks
Checking callbacks... OK

❌ Found 1 issue(s)
Please fix before committing, or run full audit:
  ./scripts/focus-audit.sh
```

---

## Integration with tv-agent

If you have the `tv-agent` orchestrator, Focus Architect can be auto-detected.

### Add to tv-agent Detection:

Edit your `tv-agent` script:

```bash
detect_agent() {
    local task_lower=$(echo "$TASK" | tr '[:upper:]' '[:lower:]')

    # Focus Architect - expanded detection
    if [[ $task_lower == *"focus"* ]] || \
       [[ $task_lower == *"navigation"* ]] || \
       [[ $task_lower == *"key"* ]] || \
       [[ $task_lower == *"conflict"* ]] || \
       [[ $task_lower == *"audit"* ]] || \
       [[ $task_lower == *"delegation"* ]] || \
       [[ $task_lower == *"handler"* ]] || \
       [[ $task_lower == *"back"* ]] || \
       [[ $task_lower == *"menu"* ]]; then
        echo "focus-architect"
        return
    fi

    # ... rest of agent detection
}

# ... rest of tv-agent
```

### Usage with Orchestrator:

```bash
# Auto-detects Focus Architect
tv-agent "Audit focus system" TopMenuScreen2.kt

# Explicit agent selection
tv-agent --agent focus-architect "Review navigation in VOD"

# With multiple files
tv-agent "Fix navigation conflict" TopMenuScreen2.kt VodWithChannels.kt
```

---

## Common Workflows

### Workflow 1: Adding New Section

```bash
# 1. Pre-implementation audit
claude code \
  -f docs/agents/focus-architect.md \
  -f FOCUS_NAVIGATION_GUIDE.md \
  -p "Pre-implementation audit for section: MYSECTION"

# 2. Implement based on recommendations
# (Create files, add delegation, etc.)

# 3. Code review
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/components/MySection.kt \
  -p "Code review of MySection navigation"

# 4. Integration check
./scripts/focus-audit.sh

# 5. Test on device
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# 6. Commit
git add .
./scripts/focus-quick-check.sh
git commit -m "Add MYSECTION navigation"
```

### Workflow 2: Fixing Regression

```bash
# 1. Identify issue
./scripts/focus-audit.sh | grep "VOD"

# 2. Check git history
git log --grep="VOD" --oneline
git blame app/src/main/java/com/example/tv/TopMenuScreen2.kt | grep "VOD"

# 3. Get resolution strategy
claude code \
  -f docs/agents/focus-architect.md \
  -f docs/focus-patterns/resolution-strategies.md \
  -p "VOD navigation regression - resolve using Strategy 5"

# 4. Apply fix
# (Edit files based on strategy)

# 5. Verify
./scripts/focus-audit.sh

# 6. Document
# Update CLAUDE.md > Recent Conflict Resolution

# 7. Commit
git commit -m "Fix: VOD navigation regression (Issue #X)"
```

### Workflow 3: Pre-Release Audit

```bash
# 1. Full audit with report
./scripts/focus-audit.sh > audit-pre-release-$(date +%Y%m%d).txt

# 2. If issues found, deep analysis
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/*.kt \
  -p "Full system conflict detection for release v0.08"

# 3. Fix all critical issues
# (Use resolution strategies)

# 4. Re-audit until clean
./scripts/focus-audit.sh

# 5. Tag release
git tag -a v0.08 -m "Release v0.08 - Focus system clean"
```

---

## Troubleshooting

### Problem: "Scripts not executable"

```bash
# Solution:
chmod +x scripts/focus-audit.sh
chmod +x scripts/focus-quick-check.sh
```

### Problem: "Focus Architect not auto-detected by tv-agent"

```bash
# Solution 1: Use explicit agent flag
tv-agent --agent focus-architect "Task description"

# Solution 2: Add detection pattern to tv-agent
# (See "Integration with tv-agent" section above)

# Solution 3: Use manual invocation
claude code -f docs/agents/focus-architect.md -p "Task"
```

### Problem: "Audit script shows too many warnings"

This is normal for first run on existing project. Prioritize:

1. **🔴 Critical**: VOD/MOJE/START/APLIKACJE delegation issues
2. **🟡 Medium**: Missing event type checks
3. **🟢 Low**: Potential conflicts (review manually)

Fix critical first, then medium, then low.

### Problem: "Don't know which pattern to use"

Use the decision tree in `delegation-patterns.md`:

- Complex multi-row navigation → Pattern 1 (Full Delegation)
- Simple grid navigation → Pattern 2 (Custom + GlobalFocusManager)
- When in doubt → Pattern 1 (safer, more modular)

### Problem: "Focus Architect gave conflicting advice"

This shouldn't happen, but if it does:

1. Check which version of docs you're using (should be v1.0+)
2. Provide more context in prompt
3. Consult resolution-strategies.md directly
4. Ask for clarification with specific scenario

---

## Best Practices

### ✅ DO:

- Run `focus-quick-check.sh` before every commit
- Use Focus Architect for pre-implementation audits
- Document all fixes in CLAUDE.md
- Follow delegation patterns consistently
- Add event type checks to all handlers
- Use callbacks (never hardcoded state)

### ❌ DON'T:

- Skip pre-implementation audits for "small" changes
- Ignore audit warnings ("will fix later")
- Copy-paste old code without checking patterns
- Add handlers without grepping for duplicates
- Manipulate parent state directly from child
- Forget to update documentation

---

## Support & Resources

### Documentation:
- **Core Agent**: `docs/agents/focus-architect.md`
- **Patterns**: `docs/focus-patterns/delegation-patterns.md`
- **Conflicts**: `docs/focus-patterns/common-conflicts.md`
- **Fixes**: `docs/focus-patterns/resolution-strategies.md`
- **Master Guide**: `FOCUS_NAVIGATION_GUIDE.md`
- **Checklist**: `KEY_EVENT_CHECKLIST.md`
- **System Rules**: `CLAUDE.md` (Key Event Management System section)

### Tools:
- **Full Audit**: `./scripts/focus-audit.sh`
- **Quick Check**: `./scripts/focus-quick-check.sh`
- **Manual Invocation**: `claude code -f docs/agents/focus-architect.md -p "..."`
- **Orchestrator**: `tv-agent "focus task" files...`

### Getting Help:
- Consult `common-conflicts.md` for known issues
- Use `resolution-strategies.md` for step-by-step fixes
- Run Focus Architect with specific scenario
- Check git history: `git log --grep="focus\|navigation"`

---

## Version History

### v1.0 (2025-10-03) - Initial Release ✅

**Created**:
- Main agent definition (`focus-architect.md`)
- Delegation patterns reference
- Common conflicts catalog
- Resolution strategies guide
- Automated audit script (`focus-audit.sh`)
- Quick check script (`focus-quick-check.sh`)
- Complete documentation (this file)

**Features**:
- Pre-implementation audits
- Code reviews
- Conflict detection
- Resolution strategies
- Automated tools
- Production-ready templates

**Status**: Production Ready

---

## Quick Reference Card

```bash
# QUICK COMMANDS

# Full audit (30 sec)
./scripts/focus-audit.sh

# Quick check (5 sec)
./scripts/focus-quick-check.sh

# Pre-implementation
claude code -f docs/agents/focus-architect.md \
  -f FOCUS_NAVIGATION_GUIDE.md \
  -p "Pre-implementation audit: [SECTION]"

# Code review
claude code -f docs/agents/focus-architect.md \
  -f path/to/component.kt \
  -p "Code review of [Component] navigation"

# Fix conflict
claude code -f docs/agents/focus-architect.md \
  -f docs/focus-patterns/resolution-strategies.md \
  -p "Resolve: [description]"

# Full detection
claude code -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/*.kt \
  -p "Full system conflict detection"
```

---

**End of Focus Architect Guide**

For updates and issues, consult project documentation or run:
```bash
./scripts/focus-audit.sh --help
```
