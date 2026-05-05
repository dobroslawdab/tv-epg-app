# KinoGridScreen + Kino Play Overlay Focus Restoration Pattern

**Status**: Production-ready (v5.5.0)
**Implementation Date**: 2026-05-05
**Affected Files**: `KinoGridScreen.kt`, `TopMenuScreen2.kt`, `MainActivity.kt`, `VodDataCache.kt`, `components/VerticalVodCard.kt`

This document describes two related features delivered in one session:

1. **KinoGridScreen** — full-screen movie grid with category picker, opened from Kino Play
2. **Kino Play overlay focus restoration** — coming back from MovieDetail returns to the exact same poster, both from the grid and from the Kino Play tab

The two are documented together because they share state (`VodDataCache.kinoChannelMap`, `savedKinoPlayFocus`) and because the navigation paths interleave (Kino Play → grid → MovieDetail → back).

---

## 1. KinoGridScreen — full-screen movie grid

### Entry points

- **CategoryIcon click** on a Kino Play channel ("Akcja", "Disney", "Gwiezdne Wojny", ...)
- **Chip click** in `Wszystkie` channel ("Akcja", "Biograficzne", "Nowości 🔥", ...)
- **`Więcej` channel** — opens grid with no filter ("Wszystkie filmy")

Navigation goes through `MainActivity.onNavigateToKinoGrid(title, prefiltered, sourceSection)`.

### Filter resolution at navigation time

`MainActivity` decides what category to preselect:

```kotlin
kinoGridInitialCategory = when {
    title == "Nowości 🔥" -> title                                   // chip alias for "Ostatnio dodane"
    VodDataCache.kinoChannelMap.containsKey(title) -> title          // channel/collection name
    else -> prefiltered?.firstOrNull()?.category                     // genre fallback (e.g. "Biograficzne" → "Biograficzny")
}
```

`kinoChannelMap` is populated by `VodScreenContent` after `produceState` finishes the per-movie classification (`Disney`, `Gwiezdne Wojny`, `Jason Statham`, `Polskie filmy`, ...). The grid pulls collection metadata from there.

### Synchronous initial state — no flash, no async restoration

The biggest UX improvement: instead of letting the grid render with default state and then `LaunchedEffect`-restoring focus/scroll later (which always caused a brief flash on the wrong card), we compute everything **synchronously at first composition** from the cache:

```kotlin
val initState = remember(initialCategory, initialFocusedMovieId, dataCount, focusedRowPinPx) {
    computeKinoGridInitialState(
        initialCategory = initialCategory,
        initialFocusedMovieId = initialFocusedMovieId,
        dataCount = dataCount,
        pinPx = focusedRowPinPx
    )
}
var allKinoContent by remember { mutableStateOf(initState.allKino) }
var filteredKinoContent by remember { mutableStateOf(initState.filtered) }
var selectedCategory by remember { mutableStateOf(initState.selectedCategory) }
var focusedRow by remember { mutableStateOf(initState.initialRow) }
var focusedCol by remember { mutableStateOf(initState.initialCol) }
val lazyGridState = rememberLazyGridState(
    initialFirstVisibleItemIndex = initState.initialScrollIdx,
    initialFirstVisibleItemScrollOffset = initState.initialScrollOffset
)
```

`computeKinoGridInitialState()` (private top-level fn) replicates the filter logic so the grid can seed itself without waiting for data load — `VodDataCache.getKinoPlayMovies()` is already populated by the time the user reaches the grid.

### Categories list

Built from two sources, **collections first**:

1. `kinoChannelMap` keys (after excluding generic genre channels and purely functional ones):

   ```kotlin
   val excludedKinoChannels = setOf(
       "Wszystkie", "Więcej",
       "SCI-FI", "ROMANS", "KOMEDIA ROMANTYCZNA", "NA POPRAWĘ HUMORU",
       "FAMILIJNE", "ANIMOWANE", "PORUSZAJĄCE HISTORIE", "FILMY GROZY",
       "DRESZCZOWCE", "HISTORIE NA FAKTACH", "DOKUMENT"
   )
   ```

2. Genres derived from `VodContent.category`, **split on commas** so composite values like `"Akcja, przygodowy"` become `"Akcja"` + `"Przygodowy"`. First letter capitalized so `"akcja"`/`"Akcja"` collapse.

3. Special alias: `"Nowości 🔥"` is mapped to the same list as `"Ostatnio dodane"` (matches the chip in the Kino Play row).

### Filter matching

```kotlin
result = when {
    selectedCategory == "Wszystkie" -> allKinoContent
    collectionMap.containsKey(selectedCategory) -> collectionMap[selectedCategory] ?: emptyList()
    else -> allKinoContent.filter { movie ->
        movie.category.split(",").any { segment ->
            segment.trim().equals(selectedCategory, ignoreCase = true)
        }
    }
}
```

### Picker UX

- `LazyColumn` (not `Column`) inside a fullscreen picker — list scrolls top-to-bottom of the screen
- `LocalBringIntoViewSpec` overridden inside the grid to return `0f` — disables Compose's auto-bring-into-view that caused the "jumping" effect when navigating left/right between middle rows
- Header title reflects the active filter: `selectedCategory` when not "Wszystkie", else `screenTitle` ("Lista Kino"), or hardcoded `"Wszystkie filmy"` for the All-filter case
- **Filter persistence across navigation**: `onCategoryChanged` callback writes back to `MainActivity.kinoGridInitialCategory`, so if user clicks a movie → MovieDetail → BACK, the grid re-mounts with the same filter

### Focus pin

`FOCUSED_ROW_PIN_Y_DP = 240` — focused row is always anchored at this Y from viewport top via `lazyGridState.animateScrollToItem(rowFirstIndex, -focusedRowPinPx)`. Row 0 is special-cased to scroll to top (so header stays visible).

### Exact-poster restoration on BACK

`kinoGridLastClickedMovieId` (in MainActivity) saves the clicked movie's ID before navigating to MovieDetail. On re-mount, `computeKinoGridInitialState()` finds that movie's index in the *already-filtered* list and seeds `focusedRow`/`focusedCol`/`lazyGridState` with the right values from frame 1.

### VerticalVodCard

- Card: 245×425, poster 225×315 (after enlargement, was 220×380 / 200×280)
- Grid gaps halved: `GRID_HORIZONTAL_GAP=5`, `GRID_VERTICAL_GAP=2`
- **Bug fix**: card had an `onClick` parameter that was never wired — added `onPreviewKeyEvent` listener that catches `Enter`/`NumPadEnter`/`DirectionCenter` and invokes the callback

---

## 2. Kino Play tab — overlay-mode focus restoration

### The problem

When Kino Play tab user clicks ENTER on a poster, `MovieDetailScreen` opens. Returning via BACK previously caused:
- Wrong tab (Start instead of Kino Play)
- Wrong row (slider instead of last channel)
- Wrong poster (first card instead of clicked one)

Root cause: the screen was a separate entry in `when (currentScreen)` so `TopMenuScreen2` unmounted on navigation. All `remember` state in `VodWithChannels` (focusedRowIndex, focusedColIndex, lazyListStates, channelFocusRequesters, focus binding) was lost.

### The solution: `movableContentOf` + outer-Box refocus

#### a) `movableContentOf` keeps TopMenuScreen2 mounted

```kotlin
val topMenuMovable = remember {
    movableContentOf {
        // ... full TopMenuScreen2(...) invocation with all its callbacks ...
    }
}

when (currentScreen) {
    NavigationScreen.TOP_MENU2 -> topMenuMovable()
    NavigationScreen.MOVIE_DETAIL -> {
        if (showTopMenuAsBase) topMenuMovable()         // base layer — same instance
        MovieDetailScreen(...)                           // on top
    }
}

val showTopMenuAsBase = currentScreen == NavigationScreen.MOVIE_DETAIL &&
    previousScreen == NavigationScreen.TOP_MENU2
```

`movableContentOf` is the Compose primitive for moving the *same* composable instance between positions in the composition tree without losing state. When `currentScreen` flips TOP_MENU2 ↔ MOVIE_DETAIL, the TopMenuScreen2 instance moves but its `remember` state survives.

`DisposableEffect(currentScreen)` for PIP cleanup updated to NOT fire when going to MOVIE_DETAIL (we're still effectively on TOP_MENU2):

```kotlin
DisposableEffect(currentScreen) {
    onDispose {
        if (currentScreen != NavigationScreen.TOP_MENU2 &&
            currentScreen != NavigationScreen.MOVIE_DETAIL &&
            pipPlayer != null) { ... }
    }
}
```

#### b) Refocus signal: `VodDataCache.kinoPlayRefocusTrigger: MutableState<Int>`

Compose-observable counter incremented by MainActivity when `MovieDetail.onBackPressed` fires (and it came from Kino Play tab). `VodWithChannels` and `VodHeroSliderV4` both watch it via `LaunchedEffect`.

State is preserved (overlay), but **Compose's actual keyboard focus owner** was taken by MovieDetail's content. After the overlay disappears, focus is null → `Box.onPreviewKeyEvent` doesn't fire → arrows don't work. The trigger tells the right composable to re-grab focus.

#### c) Channel case: refocus the outer Box, not a specific item

```kotlin
val rootBoxFocusRequester = remember { FocusRequester() }

LaunchedEffect(VodDataCache.kinoPlayRefocusTrigger.value) {
    if (VodDataCache.kinoPlayRefocusTrigger.value > 0 && focusedRowIndex >= 1) {
        // Anti-hijack: re-assert "we're inside the section" so TopMenuScreen2's
        // line ~1607 effect (if currentRow==0 → requestFocus(menuItem)) doesn't snatch.
        globalFocusState.value = globalFocusState.value.copy(currentRow = focusedRowIndex)
        kotlinx.coroutines.delay(50)
        if (focusedRowIndex >= 2) {
            try { rootBoxFocusRequester.requestFocus() } catch (_: Exception) {}
        }
        // Slider (row 1): VodHeroSliderV4's own LaunchedEffect on refocusTriggerKey handles it.
    }
}

Box(
    modifier = Modifier
        .fillMaxSize()
        .focusRequester(rootBoxFocusRequester)
        .onPreviewKeyEvent { event -> handleVodNavigation(...) }
        .focusable()
) { ... }
```

**Why focus the Box and not the saved poster?** The original Kino Play pattern is:
- `focusedColIndex` stays at `0` for regular channels
- Visual focus indicator tracks `lazyListState.firstVisibleItemIndex` via `isItemFocused = ... && focusedColIndex == 0`
- Only `Pair(row, 0)` and `Pair(row, -1)` have stable `FocusRequester`s in the map; columns >0 get `?: FocusRequester()` (fresh per recompose)

After scroll, `Pair(row, 0)` is bound to an off-screen item that LazyRow has unmounted, so `requestFocus` on it goes into limbo. Pre-allocating FRs for all columns *seemed* like a fix but broke the existing navigation pattern (`requestFocus(Pair(row, newIndex))` started actually moving Compose focus → triggered `onFocusChange` → set `focusedColIndex` to newIndex → broke the `focusedColIndex == 0` guard).

Focusing the **outer Box** sidesteps all of that: the Box is always laid out, `Box.onPreviewKeyEvent` fires, `handleVodNavigation` reads the preserved `focusedRowIndex/focusedColIndex/lazyListState` and does the right thing. The visual focus indicator on the saved poster keeps working through the unchanged `isItemFocused == firstVisibleItemIndex` pattern.

#### d) Slider case: external refocus parameter

`VodHeroSliderV4` got a new `refocusTriggerKey: Int = 0` parameter:

```kotlin
LaunchedEffect(refocusTriggerKey) {
    if (refocusTriggerKey > 0 && isFocused) {
        showTrailer = false                            // stop any playing trailer
        suppressTrailerAfterOverlayBack = true         // prevent auto-restart
        kotlinx.coroutines.delay(50)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
}
LaunchedEffect(currentSlide, externalButtonIndex, isOnChannelsBelow) {
    if (suppressTrailerAfterOverlayBack) suppressTrailerAfterOverlayBack = false
}
```

The pre-existing `LaunchedEffect(isFocused)` only re-grabs focus on isFocused **transitions** — when overlay open/close keeps `isFocused=true` throughout, the parameter never changes and the effect doesn't re-fire. Hence the explicit external trigger.

The trailer auto-play loop got an extra guard:

```kotlin
LaunchedEffect(stableItem?.title, isOnChannelsBelow, suppressTrailerAfterOverlayBack) {
    while (!isOnChannelsBelow) {
        if (!showTrailer && !trailerUrl.isNullOrBlank() && !suppressTrailerAfterOverlayBack) {
            // ... start trailer after delay ...
        }
    }
}
```

UX: after BACK, user lands on the slider showing a static poster (not a re-playing trailer). On any key (slide change, button switch) the suppression lifts and trailer behavior resumes.

#### e) MainActivity wiring

```kotlin
NavigationScreen.MOVIE_DETAIL -> {
    if (showTopMenuAsBase) topMenuMovable()           // overlay base
    selectedMovieData?.let { movieData ->
        MovieDetailScreen(
            item = movieData,
            onBackPressed = {
                if (previousScreen == NavigationScreen.KINO_GRID) {
                    currentScreen = NavigationScreen.KINO_GRID
                } else {
                    // Kino Play tab: TopMenuScreen2 still mounted under us.
                    // Trigger refocus so VodWithChannels regains keyboard focus.
                    VodDataCache.kinoPlayRefocusTrigger.value =
                        VodDataCache.kinoPlayRefocusTrigger.value + 1
                    currentScreen = NavigationScreen.TOP_MENU2
                    savedTelewizjaSection = "KINO_PLAY"
                }
            },
            ...
        )
    }
}
```

---

## 3. Failed approaches — what NOT to do

These were tried and reverted; documented so future iterations don't repeat them.

### ❌ Pre-allocating FRs for all columns

```kotlin
// Tried, reverted
else -> {
    put(Pair(adjustedRowIndex, -1), FocusRequester())
    repeat(60) { col -> put(Pair(adjustedRowIndex, col), FocusRequester()) }
}
```

This made `requestFocus(Pair(row, newIndex))` actually move Compose focus, which triggered `onFocusChanged` on the new item, which set `focusedColIndex = newIndex`. The existing `isItemFocused = ... && focusedColIndex == 0` guard then went false → visual focus disappeared after the second navigation step.

### ❌ Restoring exact poster via dynamic FR registration + scrollToItem

```kotlin
// Tried, reverted
LaunchedEffect(Unit) {
    if (initialFocusRestore != null) {
        val firstVis = initialFocusRestore.listFirstVisible
        channelFocusRequesters[Pair(focusedRowIndex, firstVis)] = FocusRequester()
        delay(150)
        lazyListStates[channelIdx]?.scrollToItem(firstVis)
        delay(150)
        channelFocusRequesters[Pair(focusedRowIndex, firstVis)]?.requestFocus()
    }
}
```

Multiple races:
- `miniaturesYOffset` 350ms slide-down animation overlapping with the scroll
- `LaunchedEffect(focusedRow, ...)` re-scrolling on `focusedRow` change
- `LaunchedEffect(selectedCategory, ...)` resetting `focusedRow=0` when filter applied
- `VodScreenContent.LaunchedEffect(currentRow)` bumping `resetTrigger` on the default `currentRow=0` after mount

User reported "focus appears for a moment then disappears" — exactly the race signature.

### ❌ Initializing `LazyListState(firstVisibleItemIndex = saved)`

Initial scroll position seemed correct on first render but Compose's first layout pass apparently doesn't honor it cleanly when other state is also being set concurrently. Combined with the FR pre-registration above, the focused item ended up on the wrong column.

### ❌ Restoration via remount (without overlay)

Even with all the synchronization tricks, the fundamental problem was: TopMenuScreen2 unmount → all internal Compose focus tracking reset → no good way to reliably re-target a specific LazyRow item from outside. The overlay (`movableContentOf`) avoids the problem entirely by keeping the composable mounted.

---

## 4. Anti-pattern warnings

1. **Don't change `focusedColIndex == 0` to `>= 0`** in `VodUnifiedChannelRow` without also reverting FR pre-allocation. The two changes only make sense together.
2. **Don't add per-item navigation logic to `handleVodNavigation`** for `focusedColIndex > 0` in regular channels. The pattern is: focusedColIndex stays 0, LazyRow scrolls, visual focus follows `firstVisibleItemIndex`.
3. **Don't try to focus an off-screen LazyRow item.** Focus the parent Box (which has `onPreviewKeyEvent`) instead. Compose actual focus needs to be on something laid out.
4. **Don't `remember`-snapshot `VodDataCache.savedKinoPlayFocus` in multiple places.** The first reader consumes it (`also { ... = null }`), subsequent readers see null. With overlay this isn't an issue (no remount triggers), but if overlay is bypassed, only `VodWithChannels.initialFocusRestore` should consume.

---

## 5. Files modified

| File | Change |
|------|--------|
| `MainActivity.kt` | `movableContentOf { TopMenuScreen2(...) }`, refocus trigger increment in MovieDetail.onBackPressed, `kinoGridInitialCategory`/`kinoGridLastClickedMovieId` plumbing for grid |
| `TopMenuScreen2.kt` | `VodWithChannels`: `rootBoxFocusRequester`, `kinoPlayRefocusTrigger` LaunchedEffect, `hasSeenFirstCurrentRow` skip in `VodScreenContent`. `VodHeroSliderV4`: `refocusTriggerKey` parameter + LaunchedEffect, `suppressTrailerAfterOverlayBack` |
| `KinoGridScreen.kt` | `computeKinoGridInitialState()`, synchronous `remember`-seeded state, `LazyListState` initial scroll, fullscreen `LazyColumn` picker, `LocalBringIntoViewSpec` override, `onMovieClicked` + `onCategoryChanged` callbacks, `initialFocusedMovieId` restore |
| `VodDataCache.kt` | `kinoChannelMap`, `savedKinoPlayFocus` data class, `kinoPlayRefocusTrigger` `MutableState<Int>` |
| `components/VerticalVodCard.kt` | `onPreviewKeyEvent` for Enter/Center → `onClick`, larger dimensions (245×425 / 225×315) |

---

## 6. Quick reference — when adding a new section

If you need to give another section the same overlay-back-to-poster behavior:

1. Add a section-specific `MutableState<Int>` to `VodDataCache` (mirror `kinoPlayRefocusTrigger`).
2. In MainActivity, wrap the section's TopMenuScreen2-equivalent in `movableContentOf`. Render it both for its normal currentScreen AND under the overlay.
3. In MainActivity's `MovieDetail.onBackPressed`, increment the trigger when the source was that section.
4. In the section's content composable, add a `rootBoxFocusRequester` on the Box that handles keys. Add a `LaunchedEffect(trigger)` that requestFocus()es it (with a 50ms delay).
5. If the section has a slider, mirror the `VodHeroSliderV4.refocusTriggerKey` pattern.
6. If trailer/video should NOT auto-restart after BACK, mirror the `suppressTrailerAfterOverlayBack` pattern — set on trigger, clear on first interaction.
