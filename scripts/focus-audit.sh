#!/bin/bash

# Focus Architect - Automated Conflict Detection
# Run this before committing focus-related changes

echo "🔍 Focus System Audit"
echo "===================="
echo ""

PROJECT_ROOT="/Users/uxellenceuxe/TV_componenty"
cd "$PROJECT_ROOT" || exit 1

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 1. Find all key handlers
echo "📋 Scanning for key handlers..."
echo ""

BACK_HANDLERS=$(grep -r "Key\.Back" app/src/ 2>/dev/null | wc -l | tr -d ' ')
UP_HANDLERS=$(grep -r "Key\.DirectionUp" app/src/ 2>/dev/null | wc -l | tr -d ' ')
DOWN_HANDLERS=$(grep -r "Key\.DirectionDown" app/src/ 2>/dev/null | wc -l | tr -d ' ')
LEFT_HANDLERS=$(grep -r "Key\.DirectionLeft" app/src/ 2>/dev/null | wc -l | tr -d ' ')
RIGHT_HANDLERS=$(grep -r "Key\.DirectionRight" app/src/ 2>/dev/null | wc -l | tr -d ' ')

echo "Key.Back: $BACK_HANDLERS handlers"
echo "Key.DirectionUp: $UP_HANDLERS handlers"
echo "Key.DirectionDown: $DOWN_HANDLERS handlers"
echo "Key.DirectionLeft: $LEFT_HANDLERS handlers"
echo "Key.DirectionRight: $RIGHT_HANDLERS handlers"
echo ""

# 2. Check delegation in TopMenuScreen2
echo "📐 Checking TopMenuScreen2 delegation..."
echo ""

# Check if TopMenuScreen2 exists
if [ ! -f "app/src/main/java/com/example/tv/TopMenuScreen2.kt" ]; then
    echo -e "${YELLOW}⚠️  TopMenuScreen2.kt not found${NC}"
else
    VOD_DELEGATION=$(grep -A 1 '"VOD"' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null | grep -c "false")
    MOJE_DELEGATION=$(grep -A 1 '"MOJE"' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null | grep -c "false")
    START_DELEGATION=$(grep -A 1 '"START"' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null | grep -c "false")
    APLIKACJE_DELEGATION=$(grep -A 1 '"APLIKACJE"' app/src/main/java/com/example/tv/TopMenuScreen2.kt 2>/dev/null | grep -c "false")

    if [ "$VOD_DELEGATION" -gt 0 ]; then
        echo -e "${GREEN}✅ VOD: Properly delegated${NC}"
    else
        echo -e "${RED}❌ VOD: Missing delegation or custom handling detected!${NC}"
    fi

    if [ "$MOJE_DELEGATION" -gt 0 ]; then
        echo -e "${GREEN}✅ MOJE: Properly delegated${NC}"
    else
        echo -e "${YELLOW}⚠️  MOJE: Check delegation${NC}"
    fi

    if [ "$START_DELEGATION" -gt 0 ]; then
        echo -e "${GREEN}✅ START: Properly delegated${NC}"
    else
        echo -e "${YELLOW}⚠️  START: Check delegation${NC}"
    fi

    if [ "$APLIKACJE_DELEGATION" -gt 0 ]; then
        echo -e "${GREEN}✅ APLIKACJE: Properly delegated${NC}"
    else
        echo -e "${YELLOW}⚠️  APLIKACJE: Check delegation${NC}"
    fi
fi

echo ""

# 3. Check for onReturnToMenu callbacks
echo "🔄 Checking onReturnToMenu callbacks..."
echo ""

CALLBACKS=$(grep -r "onReturnToMenu" app/src/ 2>/dev/null | wc -l | tr -d ' ')
echo "Found $CALLBACKS callback references"

if [ "$CALLBACKS" -ge 8 ]; then
    echo -e "${GREEN}✅ Callbacks present in sections${NC}"
elif [ "$CALLBACKS" -ge 4 ]; then
    echo -e "${YELLOW}⚠️  Moderate callback count - verify all sections${NC}"
else
    echo -e "${RED}❌ Low callback count - callbacks may be missing${NC}"
fi

echo ""

# 4. Check for event type verification
echo "⌨️  Checking event type verification..."
echo ""

TYPE_CHECKS=$(grep -r "KeyEventType\.KeyDown" app/src/ 2>/dev/null | wc -l | tr -d ' ')
KEY_HANDLERS=$(grep -r "onPreviewKeyEvent\|onKeyEvent" app/src/ 2>/dev/null | wc -l | tr -d ' ')

echo "Event type checks: $TYPE_CHECKS"
echo "Total key handlers: $KEY_HANDLERS"

if [ "$KEY_HANDLERS" -gt 0 ]; then
    COVERAGE=$((TYPE_CHECKS * 100 / KEY_HANDLERS))
    if [ "$COVERAGE" -ge 80 ]; then
        echo -e "${GREEN}✅ Good coverage: ${COVERAGE}%${NC}"
    elif [ "$COVERAGE" -ge 50 ]; then
        echo -e "${YELLOW}⚠️  Moderate coverage: ${COVERAGE}% (aim for 80%+)${NC}"
    else
        echo -e "${RED}❌ Low coverage: ${COVERAGE}% (many handlers missing type checks)${NC}"
    fi
fi

echo ""

# 5. Look for potential conflicts (same key, same file)
echo "⚠️  Potential conflicts..."
echo ""

CONFLICT_FOUND=0

# Check for multiple handlers in same files
for file in $(grep -l "onPreviewKeyEvent\|onKeyEvent" app/src/main/java/com/example/tv/*.kt 2>/dev/null); do
    HANDLER_COUNT=$(grep -c "onPreviewKeyEvent\|onKeyEvent" "$file")
    if [ "$HANDLER_COUNT" -gt 1 ]; then
        echo -e "${YELLOW}⚠️  $(basename "$file"): $HANDLER_COUNT handlers (check for conflicts)${NC}"
        CONFLICT_FOUND=1
    fi
done

if [ "$CONFLICT_FOUND" -eq 0 ]; then
    echo -e "${GREEN}✅ No obvious conflicts detected${NC}"
fi

echo ""

# 6. Check for hardcoded state manipulation
echo "🔒 Checking for hardcoded state manipulation..."
echo ""

HARDCODED=$(grep -r "currentSection\.value =" app/src/main/java/com/example/tv/components/ 2>/dev/null | wc -l | tr -d ' ')

if [ "$HARDCODED" -eq 0 ]; then
    echo -e "${GREEN}✅ No hardcoded state manipulation in components${NC}"
else
    echo -e "${RED}❌ Found $HARDCODED instances of hardcoded state manipulation${NC}"
    grep -n "currentSection\.value =" app/src/main/java/com/example/tv/components/*.kt 2>/dev/null | head -5
fi

echo ""

# 7. Summary
echo "===================="
echo "📊 Summary"
echo "===================="
echo ""

TOTAL_ISSUES=0

if [ "$VOD_DELEGATION" -eq 0 ]; then
    ((TOTAL_ISSUES++))
fi

if [ "$CALLBACKS" -lt 4 ]; then
    ((TOTAL_ISSUES++))
fi

if [ "$KEY_HANDLERS" -gt 0 ]; then
    COVERAGE=$((TYPE_CHECKS * 100 / KEY_HANDLERS))
    if [ "$COVERAGE" -lt 50 ]; then
        ((TOTAL_ISSUES++))
    fi
fi

if [ "$HARDCODED" -gt 0 ]; then
    ((TOTAL_ISSUES++))
fi

if [ "$TOTAL_ISSUES" -eq 0 ]; then
    echo -e "${GREEN}✅ All checks passed! Focus system looks healthy.${NC}"
else
    echo -e "${YELLOW}⚠️  Found $TOTAL_ISSUES potential issue(s)${NC}"
    echo "Review warnings above and consult:"
    echo "  - docs/focus-patterns/common-conflicts.md"
    echo "  - docs/focus-patterns/resolution-strategies.md"
fi

echo ""
echo "Next steps:"
echo "1. Review any warnings above"
echo "2. Run manual tests if changes made"
echo "3. Update FOCUS_NAVIGATION_GUIDE.md if needed"
echo "4. Consult Focus Architect for complex issues"
echo ""

exit 0
