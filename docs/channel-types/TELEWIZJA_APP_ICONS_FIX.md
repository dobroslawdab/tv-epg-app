# TELEWIZJA app-icons Fix - Quick Reference

**Date**: 2025-10-15
**Issue**: DirectionRight scrolling not working for app-icons channels in TELEWIZJA
**Status**: ✅ Fixed and documented

---

## 🎯 Problem Summary

App-icons channels in TELEWIZJA sekcji ("Dla dzieci", "Dokumenty", "Filmy i seriale HBO", "Informacyjne") were visually correct but scrolling didn't work.

**Symptom**: User could focus CategoryIcon and first item, but pressing RIGHT didn't scroll to next items.

---

## 🔍 Root Cause

```kotlin
// gridContent had emptyList() for app-icons channels
val gridContent = remember {
    channels.associateWith { channelName ->
        when (channelName) {
            "Dla dzieci", "Dokumenty", "HBO", "Informacyjne" -> emptyList()  // ❌
            else -> vodContentList.shuffled().take(10)
        }
    }
}

// Navigation handler used gridContent for maxIndex
val rowContent = gridContent[channelName] ?: emptyList()  // Returns emptyList()
val maxIndex = rowContent.size - 1  // = -1

if (lazyListState.firstVisibleItemIndex < maxIndex) {  // NEVER true!
    // Scrolling NEVER happens
}
```

**Why?** TELEWIZJA app-icons use `TvChannel` data from JSON, not `VodContent`. The actual data was in separate lists (`kidsChannels`, `docChannels`, etc.) but navigation handler didn't know about them.

---

## ✅ Solution

### 1. Create appIconsData Map

```kotlin
// After gridContent definition (TopMenuScreen2.kt:1423-1431)
val appIconsData = remember {
    mapOf(
        "Dla dzieci" to kidsChannels,
        "Dokumenty" to docChannels,
        "Filmy i seriale HBO" to hboChannels,
        "Informacyjne" to newsChannels
    )
}
```

### 2. Pass to Navigation Handler

```kotlin
// TopMenuScreen2.kt:1618
handleTelewizjaNavigation(
    // ... other params
    gridContent = gridContent,
    appIconsData = appIconsData,  // ⭐ NEW
    onReturnToMenu = onReturnToMenu
)
```

### 3. Update Handler Signature

```kotlin
// TopMenuScreen2.kt:4139
fun handleTelewizjaNavigation(
    // ... other params
    gridContent: Map<String, List<VodContent>>,
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),  // ⭐ NEW
    onReturnToMenu: () -> Unit
): Boolean
```

### 4. Fix maxIndex Calculation

```kotlin
// TopMenuScreen2.kt:4263-4273
Key.DirectionRight -> {
    val channelName = channels.getOrNull(focusedRowIndex) ?: ""

    // ⭐ Check appIconsData FIRST, then gridContent
    val maxIndex = when {
        appIconsData.containsKey(channelName) -> {
            val channelList = appIconsData[channelName] ?: emptyList()
            channelList.size - 1
        }
        else -> {
            val rowContent = gridContent[channelName] ?: emptyList()
            rowContent.size - 1
        }
    }

    if (lazyListState.firstVisibleItemIndex < maxIndex) {
        coroutineScope.launch {
            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
        }
    }
}
```

---

## 📊 Data Sources

### TvChannel (TELEWIZJA)
```kotlin
data class TvChannel(
    val id: String,
    val name: String,
    val logo: String,      // Remote URL from JSON
    val category: String,
    val streamUrl: String
)
```

**Source**: `tv_channels.json` → filtered by category

**Categories**:
- `"dla-dzieci"` → Kids channels
- `"dokumenty"` → Documentary channels
- `"hbo"` → HBO channels
- `"informacyjne"` → News channels

### AppItem (APLIKACJE)
```kotlin
data class AppItem(
    val id: String,
    val name: String,
    val iconResId: Int  // Local drawable
)
```

**Source**: Hardcoded lists in component

**Channels**:
- "Ostatnio używane"
- "Aplikacje"

---

## 🧪 Testing Results

All test cases passed after fix:

✅ **Focus Navigation**: CategoryIcon → Content works
✅ **Scrolling RIGHT**: Scrolls through all channels (e.g., 3 kids channels)
✅ **Scrolling LEFT**: Returns to start, then to CategoryIcon
✅ **Channel Name**: Appears above list when scrolled
✅ **CategoryIcon Alpha**: Fades out when scrolled
✅ **No Crashes**: Stable navigation

---

## 📝 Key Learnings

### Pattern: Dual Data Sources

When app-icons channel uses **different data type** than gridContent:

1. ✅ Create separate map for that data type
2. ✅ Pass both maps to navigation handler
3. ✅ Check special map FIRST, then fallback to gridContent
4. ✅ Use default parameter for backward compatibility

### Anti-Pattern: Single Data Source Assumption

❌ **DON'T** assume all channels use same data structure
✅ **DO** support multiple data sources with priority check

---

## 🔗 Related Documentation

- [APP_ICONS.md](./APP_ICONS.md) - Complete app-icons documentation (v2.0)
- Section: "TELEWIZJA Implementation with TvChannel Data" (line 732+)
- Section: "Data Source Patterns" (line 1055+)
- Section: "Navigation with Multiple Data Sources" (line 488+)

---

## 📋 Implementation Checklist

If implementing similar fix elsewhere:

- [ ] Identify data source mismatch (TvChannel vs VodContent vs AppItem)
- [ ] Create dedicated map for alternative data source
- [ ] Add parameter to navigation handler (default = emptyMap())
- [ ] Update maxIndex calculation (check alternative map first)
- [ ] Test scrolling LEFT and RIGHT
- [ ] Verify channel name and CategoryIcon behavior
- [ ] Document in APP_ICONS.md

---

**Author**: Claude Code
**Approved**: 2025-10-15
**Status**: Production-ready
