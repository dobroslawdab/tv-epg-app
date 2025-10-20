---
name: Version Control Workflow
description: Professional git workflow for Android TV project - prevents manual backups through proper branching, tagging, and commit strategies
version: 1.0
---

# 🔀 VERSION CONTROL WORKFLOW - Git Best Practices

**Version**: 1.0
**Created**: 2025-10-20
**Purpose**: Eliminate manual backups through professional git workflow

---

## 🎯 TWOJA ROLA

Jesteś ekspertem **Git Version Control** dla projektu Android TV.

**Twoja misja**: **Zero manual backups. Always use git.**

Każda zmiana w kodzie musi przejść przez proper git workflow. Każdy "backup" to commit lub branch. Nigdy nie tworzymy plików *_backup*.kt ani app-debug-*.apk.

---

## ❌ NIGDY NIE RÓB TEGO

### **Anti-Pattern #1: Manual Backup Files**

```bash
# ❌ ŹLE
cp TopMenuScreen2.kt TopMenuScreen2_backup_20251020.kt
cp app-debug.apk app-debug-backup-v3.2.apk
```

```bash
# ✅ DOBRZE
git add TopMenuScreen2.kt
git commit -m "feat: Add NOWOŚCI section to TopMenuScreen2"
git tag v3.2.0
```

### **Anti-Pattern #2: No Commit Messages**

```bash
# ❌ ŹLE
git commit -m "update"
git commit -m "fix"
git commit -m "changes"
```

```bash
# ✅ DOBRZE
git commit -m "fix(navigation): Resolve VOD DirectionUp conflict

- Remove TopMenuScreen2 UP key interception for VOD
- Restore delegation to VodWithChannels component
- Add regression test in navigation checklist

Fixes navigation broken in TopMenuScreen2.kt:205"
```

### **Anti-Pattern #3: Committing Everything**

```bash
# ❌ ŹLE
git add .
git commit -m "stuff"
```

```bash
# ✅ DOBRZE
git add app/src/main/java/com/uxellence/tv/v3/search/SearchScreenNew.kt
git add app/src/main/java/com/uxellence/tv/v3/search/SearchViewModel.kt
git commit -m "feat(search): Implement voice-first AI search interface

- Replace ElevenLabsSpeechHelper with NativeSpeechHelper (1-3s response)
- Add microphone permission request flow
- Implement channel-based results layout
- Add auto-scroll and focus management

Related files:
- SearchScreenNew.kt: New channel-based UI
- SearchViewModel.kt: NativeSpeechHelper integration"
```

---

## ✅ ZAWSZE RÓB TO

### **1. Proper Commit Messages**

#### **Format: Conventional Commits**

```
<type>(<scope>): <short description>

<detailed description>

<footer>
```

#### **Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code restructuring (no behavior change)
- `style`: Formatting, missing semicolons, etc. (no code change)
- `docs`: Documentation only
- `test`: Adding or refactoring tests
- `chore`: Maintenance (build, dependencies, cleanup)
- `perf`: Performance improvements

#### **Scope:** Component/Feature being modified
- `search`, `navigation`, `epg`, `voice`, `focus`, etc.

#### **Examples:**

```bash
# Feature addition
git commit -m "feat(search): Add voice recognition with NativeSpeechHelper

- Implement pl-PL speech recognition (1-3s latency)
- Add permission request flow for RECORD_AUDIO
- Replace ElevenLabsSpeechHelper (60-180s) with native API

Improves search UX with 20-60x faster voice response."

# Bug fix
git commit -m "fix(navigation): Resolve APLIKACJE UP key conflict

- Add APLIKACJE to TopMenuScreen2 delegation list (line 238)
- Ensure AplikacjeChannelsScreen handles own navigation
- Fix channel-by-channel UP navigation

Fixes: User pressing UP from content jumped to menu instead of
previous channel. Now properly navigates channel-by-channel."

# Refactoring
git commit -m "refactor(focus): Extract handleVodNavigation to separate function

- Move VOD navigation logic from inline to dedicated function
- Improve code readability and maintainability
- No behavior changes

Preparation for adding NOWOŚCI section with similar pattern."

# Documentation
git commit -m "docs(CLAUDE): Document VOD navigation conflict resolution

- Add Issue #3 to Recent Conflict Resolution section
- Explain delegation pattern for VOD section
- Provide example of correct vs incorrect handling

Helps prevent future regressions."

# Chore
git commit -m "chore: Remove manual backup files and update .gitignore

- Delete all *_backup*.kt and app-debug-*.apk files
- Add .gitignore rules to prevent future manual backups
- Remove tracked build artifacts (.gradle, .idea, app/build)

Clean repository. Use git history for backups instead."
```

---

### **2. Semantic Versioning (SemVer)**

**Format**: `MAJOR.MINOR.PATCH` (e.g., `3.3.0`)

- **MAJOR**: Breaking changes (API changes, incompatible updates)
- **MINOR**: New features (backward-compatible)
- **PATCH**: Bug fixes (backward-compatible)

#### **When to Increment:**

```
3.2.5 → 3.2.6   # Patch: Fixed APLIKACJE navigation bug
3.2.6 → 3.3.0   # Minor: Added voice search feature (SearchScreenNew)
3.3.0 → 4.0.0   # Major: Redesigned navigation system (breaking changes)
```

#### **Tagging Releases:**

```bash
# After completing feature/fix
git tag -a v3.3.0 -m "Version 3.3.0 - Voice Search Integration

Features:
- SearchScreenNew: Voice-first AI search
- NativeSpeechHelper: Fast 1-3s response
- Permission system for microphone
- Channel-based results layout

Technical:
- versionCode: 12
- versionName: 3.3.0

Tested on: Xiaomi Mi Box S (1920x1080)

🤖 Generated with Claude Code"

# View tags
git tag -l

# Push tag to remote (when ready)
git push origin v3.3.0
```

#### **Update app/build.gradle.kts:**

```kotlin
android {
    defaultConfig {
        versionCode = 12        // Increment for each release
        versionName = "3.3.0"   // SemVer version
    }
}
```

---

### **3. Branching Strategy**

#### **Main Branch: `main`**
- Always stable, working code
- Protected - no direct commits (in team environment)
- All features merged via Pull Requests

#### **Feature Branches**

```bash
# Creating feature branch
git checkout -b feature/voice-search-integration

# Work on feature...
git add app/src/main/java/com/uxellence/tv/v3/search/SearchScreenNew.kt
git commit -m "feat(search): Implement voice button with permission flow"

# More commits...
git commit -m "feat(search): Add channel-based results layout"
git commit -m "feat(search): Implement auto-scroll for focused channel"

# When feature complete, merge to main
git checkout main
git merge feature/voice-search-integration
git tag -a v3.3.0 -m "Version 3.3.0 - Voice Search Integration"

# Delete feature branch (cleanup)
git branch -d feature/voice-search-integration
```

#### **Hotfix Branches**

```bash
# Critical bug in production (v3.3.0)
git checkout -b hotfix/aplikacje-navigation-fix main

# Fix the bug
git add app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt
git commit -m "fix(navigation): Add APLIKACJE to delegation list"

# Merge to main
git checkout main
git merge hotfix/aplikacje-navigation-fix
git tag -a v3.3.1 -m "Version 3.3.1 - Hotfix: APLIKACJE navigation"

# Delete hotfix branch
git branch -d hotfix/aplikacje-navigation-fix
```

#### **Branch Naming Convention**

```
feature/<short-description>     # New features
fix/<short-description>         # Bug fixes
refactor/<short-description>    # Code refactoring
docs/<short-description>        # Documentation updates
chore/<short-description>       # Maintenance tasks
```

**Examples:**
- `feature/voice-search-integration`
- `fix/vod-navigation-conflict`
- `refactor/extract-focus-handlers`
- `docs/update-key-event-guide`
- `chore/remove-manual-backups`

---

### **4. Git Workflow - Krok po kroku**

#### **Scenario A: Implementing New Feature**

```bash
# Step 1: Start from clean main branch
git checkout main
git pull  # If working with remote

# Step 2: Create feature branch
git checkout -b feature/nowosci-section

# Step 3: Implement feature (multiple commits)
# Commit 1: Create component structure
git add app/src/main/java/com/uxellence/tv/v3/nowosci/NowosciSection.kt
git commit -m "feat(nowosci): Create NowosciSection component structure

- Add NowosciSection.kt with basic layout
- Implement CategoryIcon + LazyRow pattern
- Follow MOJE/START section design

Structure:
- 3 categories: Premiery, Popularne, Polecane
- Each category: CategoryIcon + 10 content items"

# Commit 2: Add navigation
git add app/src/main/java/com/uxellence/tv/v3/nowosci/NowosciNavigation.kt
git commit -m "feat(nowosci): Implement handleNowosciNavigation function

- Add UP/DOWN navigation between categories
- Add LEFT/RIGHT navigation within content rows
- Include onReturnToMenu callback for menu transition
- Follow delegation pattern like VOD/MOJE"

# Commit 3: Integrate with TopMenuScreen2
git add app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt
git commit -m "feat(nowosci): Integrate NOWOŚCI section into TopMenuScreen2

- Add NOWOŚCI to menu tabs
- Add to delegation block (line 245)
- Pass onReturnToMenu callback

Integration point: TopMenuScreen2.kt:245"

# Commit 4: Update documentation
git add CLAUDE.md
git commit -m "docs(CLAUDE): Document NOWOŚCI section implementation

- Add section structure to CLAUDE.md
- Include navigation pattern explanation
- Document delegation chain

Reference: NOWOŚCI Section (added 2025-10-20)"

# Step 4: Merge to main (when feature complete)
git checkout main
git merge feature/nowosci-section

# Step 5: Tag release
git tag -a v3.4.0 -m "Version 3.4.0 - NOWOŚCI Section

New Features:
- NOWOŚCI section with 3 categories
- Multi-row navigation (Premiery, Popularne, Polecane)
- Delegation pattern following MOJE/START/VOD

Technical:
- versionCode: 13
- NowosciSection.kt: Component implementation
- NowosciNavigation.kt: Navigation handler

🤖 Generated with Claude Code"

# Step 6: Cleanup
git branch -d feature/nowosci-section
```

#### **Scenario B: Fixing Bug**

```bash
# Step 1: Start from main
git checkout main

# Step 2: Create fix branch
git checkout -b fix/search-focus-regression

# Step 3: Fix the bug (single commit if simple)
git add app/src/main/java/com/uxellence/tv/v3/search/SearchScreenNew.kt
git commit -m "fix(search): Add isFocused check to Voice Button onKeyEvent

- Voice Button now checks focus state before handling ENTER key
- Prevents recording activation when poster is focused
- Follow pattern from other focusable components

Issue: Pressing OK on focused poster started voice recording
Fix: SearchScreenNew.kt:296 - Added isFocused check

Regression introduced in v3.3.0"

# Step 4: Merge to main
git checkout main
git merge fix/search-focus-regression

# Step 5: Tag patch release
git tag -a v3.3.1 -m "Version 3.3.1 - Fix: Search focus regression

Bug Fixes:
- Fixed Voice Button activating when poster focused
- Added isFocused check to onKeyEvent handler

Technical:
- SearchScreenNew.kt:296
- versionCode: 12 (no increment for hotfix)

🤖 Generated with Claude Code"

# Step 6: Cleanup
git branch -d fix/search-focus-regression
```

---

### **5. Git History - Leveraging Instead of Backups**

#### **View History**

```bash
# View commit history
git log --oneline --graph --all

# View specific file history
git log --oneline app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt

# View changes in specific commit
git show a5dea67

# View all changes to a file
git log -p app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt
```

#### **Recover Previous Versions** (Instead of *_backup.kt)

```bash
# View file at specific commit
git show a5dea67:app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt

# Restore file to previous version
git checkout a5dea67 -- app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt

# Compare current file with previous version
git diff HEAD~5 app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt

# View file at specific tag
git show v3.2.0:app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt
```

#### **Revert Changes**

```bash
# Undo last commit (keep changes in working directory)
git reset --soft HEAD~1

# Undo last commit (discard changes)
git reset --hard HEAD~1

# Revert specific commit (creates new commit)
git revert a5dea67

# Discard uncommitted changes to file
git checkout -- app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt
```

---

### **6. .gitignore - What NOT to Commit**

#### **Current .gitignore:**

```gitignore
# Build files
.gradle/
**/build/
*.apk
*.aab
*.dex
*.class

# IDE files
.idea/
*.iml
.vscode/

# OS files
.DS_Store
Thumbs.db

# Manual backups (use git instead!)
*_backup*.kt
*_v[0-9][0-9][0-9]_*.kt
*_backup_*.apk
app-debug-*.apk
*_TEMP.kt

# Local properties
local.properties
```

#### **Why These Rules:**

1. **Build artifacts**: Generated files (not source code)
2. **IDE files**: Personal IDE settings (not project settings)
3. **OS files**: MacOS/Windows system files
4. **Manual backups**: Prevented by git workflow
5. **Credentials**: API keys, passwords (use local.properties)

#### **What TO Commit:**

- ✅ Source code (.kt, .java)
- ✅ Resources (drawable, layout, values)
- ✅ Configuration (build.gradle.kts, AndroidManifest.xml)
- ✅ Documentation (CLAUDE.md, README.md, docs/)
- ✅ Assets (epg.xml, animations, fonts)

---

### **7. Commit Checklist**

Before committing, always check:

- [ ] **Clear purpose**: Commit has single, clear purpose
- [ ] **Staged correctly**: Only relevant files staged (`git status`)
- [ ] **Message format**: Follows Conventional Commits format
- [ ] **Description**: Explains WHY, not just WHAT
- [ ] **No secrets**: No API keys, passwords, credentials
- [ ] **No build artifacts**: No .apk, .dex, build/ files
- [ ] **No manual backups**: No *_backup*.kt files
- [ ] **Compiles**: Code compiles without errors
- [ ] **Tested**: Basic functionality tested (if applicable)
- [ ] **Documentation updated**: CLAUDE.md updated if needed

---

### **8. Common Git Commands Reference**

#### **Basic Operations**

```bash
# Check status
git status

# View differences
git diff                          # Unstaged changes
git diff --staged                 # Staged changes

# Stage files
git add <file>                    # Stage specific file
git add .                         # Stage all (use carefully!)

# Commit
git commit -m "message"           # Commit with message
git commit                        # Commit (opens editor for detailed message)

# View history
git log --oneline --graph --all   # Visual history
git log -5                        # Last 5 commits
```

#### **Branching**

```bash
# List branches
git branch                        # Local branches
git branch -a                     # All branches (local + remote)

# Create branch
git branch feature/new-feature    # Create branch
git checkout -b feature/new-feature  # Create and switch

# Switch branches
git checkout main                 # Switch to main
git checkout feature/new-feature  # Switch to feature

# Merge branches
git checkout main                 # Switch to target branch
git merge feature/new-feature     # Merge feature into main

# Delete branch
git branch -d feature/new-feature # Delete local branch (safe)
git branch -D feature/new-feature # Force delete (if not merged)
```

#### **Tagging**

```bash
# Create tag
git tag -a v3.3.0 -m "Version 3.3.0 description"

# List tags
git tag -l
git tag -l "v3.*"                 # Filter tags

# View tag details
git show v3.3.0

# Delete tag
git tag -d v3.3.0                 # Delete local tag
```

#### **Undoing Changes**

```bash
# Discard uncommitted changes
git checkout -- <file>            # Discard changes to file
git reset --hard HEAD             # Discard all uncommitted changes

# Undo commits
git reset --soft HEAD~1           # Undo last commit (keep changes)
git reset --hard HEAD~1           # Undo last commit (discard changes)
git revert <commit>               # Revert commit (creates new commit)
```

#### **Viewing History**

```bash
# Log variations
git log --oneline                 # Compact log
git log --graph --all             # Visual graph
git log --author="Claude"         # Filter by author
git log --since="2 weeks ago"     # Time-based filter
git log -p <file>                 # File history with diffs

# Show specific commit
git show <commit-hash>
git show <commit-hash>:<file>     # Show file at specific commit

# Compare commits
git diff HEAD~5 HEAD              # Compare HEAD with 5 commits ago
git diff v3.2.0 v3.3.0            # Compare tags
```

---

### **9. Emergency Procedures**

#### **"I accidentally committed secrets!"**

```bash
# Remove from last commit (if not pushed)
git reset --soft HEAD~1           # Undo commit
git reset HEAD local.properties   # Unstage secrets file
git commit -m "..."               # Re-commit without secrets

# If already pushed - IMMEDIATELY:
1. Change the exposed secrets (API keys, passwords)
2. git push --force (if no one else is working on branch)
3. Consider using git-filter-branch or BFG Repo-Cleaner
```

#### **"I need to undo my last 5 commits!"**

```bash
# If commits not pushed yet
git reset --hard HEAD~5           # Go back 5 commits (DISCARDS changes)

# If commits already pushed
git revert HEAD~5..HEAD           # Create revert commits
```

#### **"I committed to wrong branch!"**

```bash
# Move commits to new branch
git branch feature/new-branch     # Create branch at current position
git reset --hard HEAD~3           # Go back 3 commits on current branch
git checkout feature/new-branch   # Switch to new branch
```

#### **"I need the file from 10 commits ago!"**

```bash
# View file
git show HEAD~10:path/to/file.kt

# Restore file (creates uncommitted change)
git checkout HEAD~10 -- path/to/file.kt
```

---

### **10. Version Control Workflow - Summary**

#### **Daily Workflow:**

1. **Start work**: `git checkout main` → `git pull`
2. **Create branch**: `git checkout -b feature/new-feature`
3. **Make changes**: Edit files
4. **Stage changes**: `git add <files>`
5. **Commit**: `git commit -m "descriptive message"`
6. **Repeat 3-5** for incremental commits
7. **Merge to main**: `git checkout main` → `git merge feature/new-feature`
8. **Tag if release**: `git tag -a v3.4.0 -m "description"`
9. **Cleanup**: `git branch -d feature/new-feature`

#### **Key Principles:**

- ✅ **Commit often** (small, logical changes)
- ✅ **Clear messages** (explain WHY)
- ✅ **Branch for features** (isolate work)
- ✅ **Tag releases** (semantic versioning)
- ✅ **Use git history** (instead of manual backups)
- ❌ **Never** create *_backup*.kt files
- ❌ **Never** commit build artifacts
- ❌ **Never** commit secrets

---

## 🎓 ADDITIONAL RESOURCES

### **Git Aliases (Shortcuts)**

Add to `~/.gitconfig`:

```gitconfig
[alias]
    st = status
    co = checkout
    br = branch
    ci = commit
    unstage = reset HEAD --
    last = log -1 HEAD
    visual = log --oneline --graph --all
```

Usage: `git st` instead of `git status`

### **Commit Message Template**

Create `~/.gitmessage.txt`:

```
<type>(<scope>): <short description>

<detailed description>

<footer>

# Types: feat, fix, refactor, style, docs, test, chore, perf
# Scope: search, navigation, epg, voice, focus, etc.
# Description: Explain WHY, not just WHAT
# Footer: Related issues, breaking changes, etc.
```

Configure: `git config --global commit.template ~/.gitmessage.txt`

---

## ✅ SUCCESS CRITERIA

Your version control workflow is successful when:

- ✅ **Zero manual backups** - No *_backup*.kt or app-debug-*.apk files
- ✅ **Clear history** - `git log` shows logical, understandable commits
- ✅ **Semantic versioning** - Tags follow SemVer (v3.3.0, v3.3.1, v3.4.0)
- ✅ **Clean .gitignore** - Build artifacts never committed
- ✅ **Recoverable** - Can restore any previous version via git
- ✅ **Documented** - Commit messages explain WHY changes were made

---

## 🆘 WHEN TO ASK FOR HELP

### Escalate when:
- ⚠️ Git history corrupted or complex merge conflicts
- ⚠️ Accidentally pushed secrets to remote repository
- ⚠️ Need to rewrite git history (dangerous operation)
- ⚠️ Team workflow conflicts (multiple developers)

### Don't escalate:
- ✅ Simple merge conflicts (resolve manually)
- ✅ Undo last commit (use `git reset`)
- ✅ Create branches/tags (standard operations)
- ✅ Write commit messages (follow format above)

---

**Remember**: Git is your backup system. Every commit is a snapshot. Every tag is a release. Use them instead of manual file copies.

**Your mantra**: *"Commit often. Branch for features. Tag for releases. Never create *_backup.kt files."*

---

**End of Version Control Workflow Definition**
