# Rental Flow Pattern — Payment Processing → "Oglądaj" + Countdown

**Status**: Production-ready (v5.5.1)
**Implementation Date**: 2026-05-06
**Affected Files**: `rental/RentalManager.kt`, `moviedetail/RentalProcessingScreen.kt`, `moviedetail/MovieDetailScreen.kt`, `MainActivity.kt`, `MojeContentCache.kt`, `TopMenuScreen2.kt`

End-to-end rental UX: from clicking "Wypożyczam i płacę" in PurchaseScreen, through animated processing screen, to MovieDetailScreen reflecting "rented" state with countdown bar, plus integration with `MOJE → Wypożyczone` channel and a debug-clear action under the "0" key.

This pattern builds on top of `KINO_GRID_AND_OVERLAY_FOCUS_PATTERN.md` — TopMenuScreen2 stays mounted via `movableContentOf` through the entire purchase chain.

---

## 1. Persistence — `RentalManager`

**File**: `app/src/main/java/com/uxellence/tv/v3/rental/RentalManager.kt`

```kotlin
object RentalManager {
    val rentals: MutableState<Map<String, Long>> = mutableStateOf(emptyMap())
    fun init(context: Context)
    fun rent(movieId: String, context: Context, durationMs: Long = 48*60*60*1000)
    fun isRented(movieId: String): Boolean
    fun expiresAt(movieId: String): Long?
    fun remainingMs(movieId: String): Long?
    fun rentedMovieIds(): List<String>
    fun clearAll(context: Context)
}
```

**Key design points:**

- `rentals` is **publicly visible Compose `MutableState`** (no `private`). Composables read `RentalManager.rentals.value` and Compose tracking auto-subscribes them — when state changes (rent / clearAll), every reader recomposes. This is what lets MovieDetailScreen, MOJE→Wypożyczone, and DevTogglesModal all stay in sync without an event bus.
- Persistence in `SharedPreferences` keyed `rental_prefs` / `rentals_v1`. Format: `"id1=expiresMs;id2=expiresMs"`. Simple, no Room dependency.
- Auto-prune wygasłych: `init()` filters out `expiresAt < now` on load. `remainingMs` returns null for expired rentals so callers naturally treat them as not-rented.
- Init must be called once early. Wired in `MainActivity.onCreate`:
  ```kotlin
  VodDataCache.initialize(this)
  com.uxellence.tv.v3.rental.RentalManager.init(this)
  ```

---

## 2. Animated processing screen — `RentalProcessingScreen`

**File**: `app/src/main/java/com/uxellence/tv/v3/moviedetail/RentalProcessingScreen.kt`

Two stages, ~1.5s each, separated by a Compose `Crossfade`:

### Stage 0 — "Przetwarzanie płatności"
- `Canvas(96x96).rotate(animatedDeg)` — three-quarter aqua arc, infinite linear rotation, `tween(1000)` per cycle
- Text below in white-bold

### Stage 1 — "Film wypożyczony!"
- `Canvas(120x120).scale(animated)` — circle outline + animated checkmark
- Checkmark drawing trick:
  ```kotlin
  val cm = Path().apply {
      moveTo(w * 0.28f, h * 0.52f)
      lineTo(w * 0.45f, h * 0.68f)
      lineTo(w * 0.74f, h * 0.36f)
  }
  val measure = PathMeasure().apply { setPath(cm, false) }
  val drawn = Path()
  measure.getSegment(0f, total * pathProgress, drawn, true)
  drawPath(drawn, color = Color(0xFF5FEDD4), style = Stroke(stroke, cap = StrokeCap.Round))
  ```
  `pathProgress` animates 0→1 with `tween(500)` — the stroke draws itself in.
- Movie title visible underneath in muted color

### Logic
```kotlin
var stage by remember { mutableStateOf(0) }
LaunchedEffect(Unit) {
    delay(1500); stage = 1
    delay(1500); onComplete()
}
```

BACK key intentionally NOT handled — the screen is short and aborting mid-confirmation would leave inconsistent state. Pure auto-progress.

---

## 3. MovieDetailScreen — rented branch + countdown bar

**File**: `moviedetail/MovieDetailScreen.kt`

### State derivation (top of composable)
```kotlin
val rentalsMap = RentalManager.rentals.value
val rentalKey = item.title
val isRented = remember(rentalsMap, rentalKey) {
    (rentalsMap[rentalKey] ?: 0L) > System.currentTimeMillis()
}
val rentalExpiresAt = rentalsMap[rentalKey]
```

`VodSlideData` has no stable id, so we key by `title`. Same convention used in `MainActivity.onConfirmPurchase` and `MojeContentCache`.

### Buttons
```kotlin
val buttons = listOf(
    if (isRented) "Oglądaj" else "Wypożycz: ${item.price}",
    "Zwiastun"
)
// onClick:
0 -> if (isRented) onWatchClicked() else onRentClicked()
```

New `onWatchClicked` callback — currently a TODO log in MainActivity (player integration is a separate ticket).

### `RentalCountdownInfo` composable
Rendered above the buttons row only when `isRented`:

```kotlin
Column {
    Text("Możesz oglądać przez ${formatRemaining(remainingMs)}")
    Box(
        modifier = Modifier.width(360.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
            .background(Color(0x33EEEEEE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)        // shrinks as remaining time drops
                .align(Alignment.CenterEnd)    // anchored on RIGHT — left edge recedes
                .background(Color(0xFF5FEDD4))
        )
    }
}
```

**Key trick: `align(Alignment.CenterEnd)`**. When width drops below container width, the inner Box anchors to the RIGHT edge — left edge "recedes" inward as time runs out. This matches user request: *"pasek co zanika do prawej czyli z lewej ubywa"*.

### Tick mechanism
```kotlin
var tick by remember { mutableIntStateOf(0) }
LaunchedEffect(expiresAt) {
    while (true) { tick++; delay(60_000L) }
}
val remainingMs by remember(expiresAt, tick) {
    derivedStateOf { (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L) }
}
```

Re-renders once per minute — sufficient for "47h 35m" → "47h 34m" precision. No need for per-frame updates.

---

## 4. MainActivity navigation — overlay extended

### `NavigationScreen.RENTAL_PROCESSING`
Added between `PURCHASE` and `OLYMPICS`.

### `showTopMenuAsBase` extended
```kotlin
val showTopMenuAsBase = (currentScreen == MOVIE_DETAIL ||
    currentScreen == PURCHASE ||
    currentScreen == RENTAL_PROCESSING) &&
    (previousScreen == TOP_MENU2 || cameFromQuickPurchase)
```

This way TopMenuScreen2's `movableContentOf` stays rendered as a base layer through the entire chain `TOP_MENU2 → MOVIE_DETAIL → PURCHASE → RENTAL_PROCESSING → MOVIE_DETAIL → BACK`. All `VodWithChannels` state (focus, scroll, FocusRequester bindings) survives. The PIP `DisposableEffect` was also extended to skip cleanup for `RENTAL_PROCESSING`.

### `onConfirmPurchase` wiring
```kotlin
onConfirmPurchase = {
    RentalManager.rent(movieId = movieData.title, context = context)
    currentScreen = NavigationScreen.RENTAL_PROCESSING
}
```

### New when case
```kotlin
NavigationScreen.RENTAL_PROCESSING -> {
    if (showTopMenuAsBase) topMenuMovable()
    selectedMovieData?.let { movieData ->
        RentalProcessingScreen(
            movieTitle = movieData.title,
            onComplete = { currentScreen = NavigationScreen.MOVIE_DETAIL }
        )
    }
}
```

When the processing screen completes, it flips to `MOVIE_DETAIL`. The same `selectedMovieData` is still set, but now `RentalManager.rentals.value` contains the entry → `isRented = true` → buttons show "Oglądaj" + countdown. **No additional plumbing needed** because state propagation is via the observable `MutableState`.

When the user finally hits BACK from MovieDetail, the existing Issue #10 refocus chain takes over (`kinoPlayRefocusTrigger++`) and the original poster regains keyboard focus on the Kino Play tab.

---

## 5. MOJE → Wypożyczone — real rentals

**File**: `MojeContentCache.kt`

```kotlin
if (channelName == "Wypożyczone") {
    val kinoPlayMovies = VodDataCache.getKinoPlayMovies()
    if (kinoPlayMovies.isEmpty()) return emptyList()
    val rentedTitles = RentalManager.rentedMovieIds().toSet()
    if (rentedTitles.isEmpty()) return emptyList()
    return kinoPlayMovies.filter { it.title in rentedTitles }
}
// other channels: cached as before
```

Intentionally **not cached** in `channelContentCache` — rental list changes whenever user rents a movie or admin clears them, so we always query fresh.

### Cache invalidation in `MojeChannelsScreen`
**File**: `TopMenuScreen2.kt` (around line 5618)

```kotlin
val rentalsSnapshot = RentalManager.rentals.value
val gridContent = remember(isNagraniaExpanded, showNagraniaV2, rentalsSnapshot) {
    MojeContentCache.getContent(channels)
}
```

Adding `rentalsSnapshot` to remember keys forces re-collection of channel content map when rentals change → MOJE→Wypożyczone updates immediately.

---

## 6. Debug clear — `DevTogglesModal` under key "0"

**File**: `TopMenuScreen2.kt` (DevTogglesModal around line 2336)

```kotlin
val ctx = androidx.compose.ui.platform.LocalContext.current
val rentalCount = RentalManager.rentals.value.size
val items = listOf(
    Triple("Aplikacje variant", ..., onAplikacjeVariantCycle),
    Triple("Profil variant", ..., onProfileVariantCycle),
    Triple("Wypożyczone", "Wyczyść ($rentalCount)", { RentalManager.clearAll(ctx) })
)
```

Reading `RentalManager.rentals.value.size` triggers Compose tracking — the count label updates automatically right after `clearAll` (modal stays open and shows new value `0`).

User reaches it: any TopMenuScreen2 view → press `0` → DevTogglesModal opens → DOWN to the rentals item → ENTER → list cleared.

---

## 7. End-to-end navigation flow (verified scenarios)

```
Kino Play poster
  ↓ ENTER
MovieDetail "Wypożycz: 24 zł/48h"
  ↓ ENTER
PurchaseScreen "Wypożyczam i płacę"
  ↓ ENTER → RentalManager.rent(title), navigate
RentalProcessingScreen [stage 0: "Przetwarzanie płatności" 1.5s]
  ↓ auto
RentalProcessingScreen [stage 1: "Film wypożyczony!" 1.5s]
  ↓ auto onComplete()
MovieDetail "Oglądaj"  +  "Możesz oglądać przez 47h 59m"  +  countdown bar
  ↓ BACK → kinoPlayRefocusTrigger++
TopMenuScreen2 (Kino Play tab) — focus restored on the original poster
```

Plus quick-purchase path from slider:
```
Kino Play slider "Wypożycz" button
  ↓ ENTER → cameFromQuickPurchase = true
PurchaseScreen → ... → RentalProcessingScreen → MovieDetail (isRented) → BACK → slider
```

---

## 8. Anti-patterns / things to NOT change

1. **Don't make `RentalManager.rentals` private.** Composables in multiple files read it directly — it's the wire that keeps everything in sync. A wrapper function (`fun rentals(): Map<...>`) would NOT trigger Compose recomposition because Compose tracks `State.value` reads, not function calls.
2. **Don't cache "Wypożyczone" in `MojeContentCache.channelContentCache`.** The list mutates; caching would freeze it stale.
3. **Don't increment `RentalManager.rentals` mid-RentalProcessingScreen.** Rent BEFORE navigating (in `onConfirmPurchase`) so by the time MovieDetail re-appears the state is already updated. Doing it from inside `onComplete` would create a brief frame where MovieDetail shows "Wypożycz" again.
4. **Don't handle BACK in RentalProcessingScreen.** The screen is intentionally short and uninterruptible — preventing aborts also prevents inconsistent half-rented state.
5. **Don't use `id` field on `VodSlideData` for rental key.** It doesn't exist; use `title`. (`VodContent` does have `id` but rental flow always starts from `VodSlideData` so `title` is the source of truth.)

---

## 9. Future extensions (out of scope for v5.5.1)

- Real billing API integration (currently mocked)
- "Watch progress" tracking — resume position when user clicks "Oglądaj" again
- Per-rental durations parsed from `price` (e.g. "29 zł/24h" → 24h, "39 zł/72h" → 72h). Currently hardcoded 48h
- Push notifications when a rental is about to expire
- Server-side rental sync (currently device-local only)
- Countdown bar in `Wypożyczone` channel cards (mini variant per poster)
