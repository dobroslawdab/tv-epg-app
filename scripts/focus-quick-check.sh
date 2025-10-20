#!/bin/bash

# Focus Architect - Quick Pre-Commit Check
# Fast validation before git commit

PROJECT_ROOT="/Users/uxellenceuxe/TV_componenty"
cd "$PROJECT_ROOT" || exit 1

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "⚡ Focus Quick Check"
echo ""

ISSUES=0

# 1. Check VOD delegation (most common regression)
echo -n "Checking VOD delegation... "
if grep -q '"VOD" -> {' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null; then
    echo -e "${RED}FAIL${NC}"
    echo "  ❌ VOD using custom handling (should be: false)"
    ((ISSUES++))
elif grep -q '"VOD".*->.*false' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${YELLOW}SKIP${NC} (file not found)"
fi

# 2. Check for hardcoded state in components
echo -n "Checking hardcoded state... "
HARDCODED=$(grep -c "currentSection\.value =" app/src/main/java/com/example/tv/components/*.kt 2>/dev/null || echo "0")
if [ "$HARDCODED" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}"
    echo "  ❌ Found $HARDCODED instance(s) of direct state manipulation"
    ((ISSUES++))
else
    echo -e "${GREEN}OK${NC}"
fi

# 3. Check for event type verification in recently modified files
echo -n "Checking event type checks... "
MODIFIED_FILES=$(git diff --name-only --cached | grep "\.kt$" || echo "")
if [ -n "$MODIFIED_FILES" ]; then
    MISSING=0
    for file in $MODIFIED_FILES; do
        if grep -q "onPreviewKeyEvent\|onKeyEvent" "$file" 2>/dev/null; then
            if ! grep -q "KeyEventType\.KeyDown" "$file" 2>/dev/null; then
                MISSING=1
            fi
        fi
    done

    if [ "$MISSING" -eq 1 ]; then
        echo -e "${YELLOW}WARN${NC}"
        echo "  ⚠️  Some handlers may be missing event type checks"
        ((ISSUES++))
    else
        echo -e "${GREEN}OK${NC}"
    fi
else
    echo -e "${GREEN}OK${NC} (no staged files)"
fi

# 4. Check for onReturnToMenu in new sections
echo -n "Checking callbacks... "
CALLBACKS=$(grep -c "onReturnToMenu" app/src/main/java/com/example/tv/components/*.kt 2>/dev/null || echo "0")
if [ "$CALLBACKS" -lt 2 ]; then
    echo -e "${YELLOW}WARN${NC}"
    echo "  ⚠️  Low callback count - verify new sections have callbacks"
else
    echo -e "${GREEN}OK${NC}"
fi

echo ""

# Summary
if [ "$ISSUES" -eq 0 ]; then
    echo -e "${GREEN}✅ All quick checks passed!${NC}"
    echo ""
    echo "Safe to commit. For comprehensive audit, run:"
    echo "  ./scripts/focus-audit.sh"
    exit 0
else
    echo -e "${RED}❌ Found $ISSUES issue(s)${NC}"
    echo ""
    echo "Please fix before committing, or run full audit:"
    echo "  ./scripts/focus-audit.sh"
    echo ""
    echo "For help, consult:"
    echo "  docs/focus-patterns/resolution-strategies.md"
    exit 1
fi
