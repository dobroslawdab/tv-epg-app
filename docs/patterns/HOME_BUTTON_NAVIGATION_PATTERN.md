# HOME Button Navigation Pattern

**Status**: ✅ Production-ready (wersja 3.12.0)
**Implementation Date**: 2025-11-18
**Pattern Type**: Launcher Integration + State Flag Communication

---

## 📋 Spis treści

1. [Problem i cel](#problem-i-cel)
2. [Architektura rozwiązania](#architektura-rozwiązania)
3. [Implementacja krok po kroku](#implementacja-krok-po-kroku)
4. [Scenariusze nawigacji](#scenariusze-nawigacji)
5. [Logi i debugging](#logi-i-debugging)
6. [Znane ograniczenia](#znane-ograniczenia)
7. [FAQ](#faq)

---

## Problem i cel

### 🎯 Cel
Implementacja pełnej obsługi przycisku HOME na Android TV z inteligentną nawigacją PIP (Picture-in-Picture), identyczną jak klawisz "0".

### ❌ Problem początkowy
1. **HOME button nie działał wewnątrz aplikacji** - Android blokuje KEYCODE_HOME dla bezpieczeństwa
2. **Brak transferu playera do PIP** - nawigacja z fullscreen EPG do TopMenu2 traciła player
3. **Nieprawidłowa nawigacja z PIP** - HOME z TopMenu2+PIP nie wracał do fullscreen EPG

### ✅ Rozwiązanie finalne
- **3-scenariuszowa logika nawigacji** w MainActivity z inteligentnym rozpoznawaniem kontekstu
- **State flag pattern** do komunikacji MainActivity ↔ EpgDayScreen
- **Reużycie infrastruktury PIP** z klawisza "0" (onNavigateToPipMode callback)
- **Standard Android API** - onNewIntent() w LauncherActivity (bez root, bez hacków)

---

## Architektura rozwiązania

### 🏗️ Komponenty systemu

```
┌─────────────────────────────────────────────────────────────────┐
│                      ANDROID FRAMEWORK                          │
│  HOME button → Intent(ACTION_MAIN, CATEGORY_HOME)              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    LauncherActivity.kt                          │
│  onNewIntent(intent) → detect HOME press                       │
│  homePressedTrigger++                                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MainActivity.kt                            │
│  LaunchedEffect(homePressedTrigger) {                          │
│    when {                                                        │
│      PIP active → return to EPG                                 │
│      EPG active → request PIP navigation (state flag)           │
│      other → TopMenu2/START                                     │
│    }                                                             │
│  }                                                               │
└────────────────────────────┬────────────────────────────────────┘
                             │ shouldNavigateHomeWithPip = true
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      EpgDayScreen.kt                            │
│  LaunchedEffect(shouldNavigateHomeWithPip) {                   │
│    isPipMode = true                                             │
│    onNavigateToPipMode(player, streamUrl)                      │
│    onHomeNavigationComplete()  // reset flag                    │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

### 🔑 Kluczowe wzorce

#### 1. **State Flag Communication Pattern**
```kotlin
// MainActivity.kt - sender
var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }

// EpgDayScreen.kt - receiver
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        // handle navigation
        onHomeNavigationComplete()  // reset flag
    }
}
```

**Zalety:**
- ✅ Reactive - zmiana state automatycznie triggeruje logikę
- ✅ Clean separation - MainActivity nie zna szczegółów implementacji EpgDayScreen
- ✅ Testable - łatwo mockować state flag
- ✅ No memory leaks - callback reset zapobiega infinite loops

#### 2. **Callback Reset Pattern**
```kotlin
// Reset flag ZAWSZE po wykonaniu akcji
onHomeNavigationComplete = { shouldNavigateHomeWithPip = false }
```

**Czemu to ważne:**
- Zapobiega wielokrotnemu wykonaniu tej samej akcji
- Pozwala na ponowne użycie tego samego triggera
- Clean state management

#### 3. **Infrastructure Reuse Pattern**
```kotlin
// Klawisz "0" i HOME używają TEGO SAMEGO kodu
isPipMode = true  // Zapobiega zwolnieniu playera
onNavigateToPipMode(player, streamUrl)  // Transfer do PIP
```

**Zalety:**
- ✅ DRY (Don't Repeat Yourself)
- ✅ Jedna implementacja = jeden punkt utrzymania
- ✅ Konsystentne zachowanie między "0" a HOME

---

## Implementacja krok po kroku

### Krok 1: LauncherActivity - Wykrywanie HOME button

**Plik:** `app/src/main/java/com/uxellence/tv/v3/LauncherActivity.kt`

```kotlin
class LauncherActivity : ComponentActivity() {
    // State trigger - zmiana wartości informuje MainActivity o HOME press
    private val homePressedTrigger = mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // STANDARD ANDROID API: Wykryj HOME button
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)) {

            android.util.Log.d("LAUNCHER_HOME", "HOME button detected via onNewIntent")
            homePressedTrigger.value++  // Trigger nawigacji
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // TvRoot MUSI otrzymać homePressedTrigger
            TvRoot(
                startScreen = NavigationScreen.TOP_MENU2,
                homePressedTrigger = homePressedTrigger.value
            )
        }
    }
}
```

**Kluczowe punkty:**
- ✅ `onNewIntent()` - JEDYNE miejsce gdzie Android dostarcza HOME intent w launcherze
- ✅ `homePressedTrigger++` - prosty counter, każda zmiana = nowy HOME press
- ✅ `intent.hasCategory(Intent.CATEGORY_HOME)` - weryfikacja że to HOME, nie inne ACTION_MAIN

---

### Krok 2: MainActivity - Inteligentna nawigacja

**Plik:** `app/src/main/java/com/uxellence/tv/v3/MainActivity.kt`

#### 2.1 State variables (linia ~310-318)
```kotlin
@Composable
fun TvRoot(
    startScreen: NavigationScreen = NavigationScreen.HOME,
    homePressedTrigger: Int = 0
) {
    // PIP state
    var pipPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var pipStreamUrl by remember { mutableStateOf<String?>(null) }
    var pipMode by remember { mutableStateOf(false) }

    // Current screen
    var currentScreen by remember { mutableStateOf(startScreen) }

    // HOME button PIP navigation request flag
    var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }
}
```

#### 2.2 LaunchedEffect dla HOME navigation (linia 325-368)
```kotlin
// Handle HOME button navigation (from LauncherActivity)
LaunchedEffect(homePressedTrigger) {
    if (homePressedTrigger > 0) {  // Ignore initial value (0)
        android.util.Log.d("HOME_NAVIGATION", "HOME pressed (trigger=$homePressedTrigger)")

        when {
            // Scenariusz 1: PIP aktywny w TopMenu2 → Wróć do fullscreen EPG
            pipPlayer != null && pipMode && currentScreen == NavigationScreen.TOP_MENU2 -> {
                android.util.Log.d("HOME_NAVIGATION", "PIP active - returning to fullscreen EPG")

                currentScreen = NavigationScreen.EPG_DAY

                // Close PIP (same as onClosePip callback)
                pipPlayer?.stop()
                pipPlayer?.release()
                pipPlayer = null
                pipStreamUrl = null
                pipMode = false
            }

            // Scenariusz 2: Fullscreen EPG → Nawiguj do TopMenu2 z PIP (jak klawisz "0")
            currentScreen == NavigationScreen.EPG_DAY -> {
                android.util.Log.d("HOME_NAVIGATION", "EPG Day Test - requesting PIP navigation")
                shouldNavigateHomeWithPip = true  // EpgDayScreen wykryje i przeniesie player
            }

            // Scenariusz 3: Inne ekrany → Nawiguj do TopMenu2/START (bez PIP)
            else -> {
                android.util.Log.d("HOME_NAVIGATION", "Other screen - navigating to TOP_MENU2/START")

                currentScreen = NavigationScreen.TOP_MENU2
                savedTelewizjaSection = "ODKRYWAJ"  // START tab

                // Clear PIP if active
                pipPlayer?.stop()
                pipPlayer?.release()
                pipPlayer = null
                pipStreamUrl = null
                pipMode = false
            }
        }
    }
}
```

**Dlaczego `when` zamiast `if`?**
- Czytelność - 3 scenariusze są obok siebie
- Exhaustive checking - Kotlin wymusza obsługę wszystkich przypadków
- Łatwość dodawania nowych scenariuszy

#### 2.3 Przekazanie parametrów do EpgDayScreen (linia ~614-634)
```kotlin
NavigationScreen.EPG_DAY -> {
    EpgDayScreen(
        onBackPressed = { currentScreen = previousScreen },
        onNavigateToPipMode = { player, streamUrl ->
            pipPlayer = player
            pipStreamUrl = streamUrl
            pipMode = true
            savedTelewizjaSection = "ODKRYWAJ"
            previousScreen = NavigationScreen.EPG_DAY
            currentScreen = NavigationScreen.TOP_MENU2
        },
        // HOME button parameters
        shouldNavigateHomeWithPip = shouldNavigateHomeWithPip,
        onHomeNavigationComplete = { shouldNavigateHomeWithPip = false },
        sx = ::sx,
        sy = ::sy
    )
}
```

**Kluczowe:**
- `shouldNavigateHomeWithPip` - propagacja state flag do child
- `onHomeNavigationComplete` - callback reset, zapobiega memory leaks

---

### Krok 3: EpgDayScreen - Obsługa PIP navigation

**Plik:** `app/src/main/java/com/uxellence/tv/v3/epg/EpgDayScreen.kt`

#### 3.1 Function signature (linia ~120-129)
```kotlin
@Composable
fun EpgDayScreen(
    onBackPressed: () -> Unit,
    onNavigateToPipMode: (ExoPlayer?, String) -> Unit = { _, _ -> },
    initialChannelId: String? = null,
    showTopMenuOverlay: Boolean = false,
    shouldNavigateHomeWithPip: Boolean = false,  // HOME button request
    onHomeNavigationComplete: () -> Unit = {},    // Reset callback
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
```

#### 3.2 LaunchedEffect dla HOME navigation (linia ~226-241)
```kotlin
// HOME button PIP navigation (identical logic to Key "0")
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip && player != null && streamUrl.isNotEmpty()) {
        android.util.Log.d("EpgDayScreen", "HOME button pressed - Transferring to PIP mode and START tab")

        // Set PIP mode flag to prevent player release on dispose
        isPipMode = true

        // Transfer player to PIP and navigate to TopMenu2/START (same as Key "0")
        onNavigateToPipMode(player, streamUrl)

        // Notify MainActivity that navigation is complete (reset flag)
        onHomeNavigationComplete()
    }
}
```

**Kluczowe warunki:**
- `shouldNavigateHomeWithPip` - trigger od MainActivity
- `player != null` - player musi istnieć do transferu
- `streamUrl.isNotEmpty()` - musi być stream URL do kontynuacji w PIP

**isPipMode flag:**
```kotlin
var isPipMode by remember { mutableStateOf(false) }

DisposableEffect(Unit) {
    val localPlayer = ExoPlayer.Builder(context).build()
    player = localPlayer

    onDispose {
        // CRITICAL: Only release player if NOT in PIP transfer mode
        if (!isPipMode) {
            localPlayer.stop()
            localPlayer.release()
            player = null
        } else {
            android.util.Log.d("EpgDayScreen", "Player transferred to PIP - not releasing")
        }
    }
}
```

---

## Scenariusze nawigacji

### Scenariusz 1: Fullscreen EPG → HOME → TopMenu2 z PIP

**Flow:**
```
1. User: EPG Day Test (fullscreen live TV)
   ↓ Naciśnij HOME
2. Android Framework: Intent(ACTION_MAIN, CATEGORY_HOME)
   ↓
3. LauncherActivity.onNewIntent(): homePressedTrigger++
   ↓
4. MainActivity.LaunchedEffect(homePressedTrigger):
   - Wykrywa currentScreen == EPG_DAY
   - Ustawia shouldNavigateHomeWithPip = true
   ↓
5. EpgDayScreen.LaunchedEffect(shouldNavigateHomeWithPip):
   - isPipMode = true (zapobiega release playera)
   - onNavigateToPipMode(player, streamUrl)
   - onHomeNavigationComplete() (reset flag)
   ↓
6. MainActivity callback onNavigateToPipMode:
   - pipPlayer = player
   - pipMode = true
   - currentScreen = TOP_MENU2
   ↓
7. Result: TopMenu2 START z PIP w prawym dolnym rogu
```

**Logi:**
```
HOME_NAVIGATION: HOME pressed (trigger=1)
HOME_NAVIGATION: EPG Day Test - requesting PIP navigation
EpgDayScreen: HOME button pressed - Transferring to PIP mode and START tab
EpgDayScreen: Player transferred to PIP - not releasing resources
```

---

### Scenariusz 2: TopMenu2 z PIP → HOME → Fullscreen EPG

**Flow:**
```
1. User: TopMenu2 START z PIP (po scenariuszu 1)
   ↓ Naciśnij HOME
2. LauncherActivity.onNewIntent(): homePressedTrigger++
   ↓
3. MainActivity.LaunchedEffect(homePressedTrigger):
   - Wykrywa pipPlayer != null && pipMode && currentScreen == TOP_MENU2
   - currentScreen = EPG_DAY
   - pipPlayer.stop() + release()
   - pipPlayer = null, pipMode = false
   ↓
4. Result: EPG Day Test (fullscreen) bez PIP
```

**Logi:**
```
HOME_NAVIGATION: HOME pressed (trigger=2)
HOME_NAVIGATION: PIP active - returning to fullscreen EPG
```

---

### Scenariusz 3: Netflix → HOME → TopMenu2 START

**Flow:**
```
1. User: Netflix (lub inna aplikacja)
   ↓ Naciśnij HOME
2. Android Framework: uruchamia LauncherActivity (BOX TV)
   ↓
3. LauncherActivity.onCreate(): startScreen = TOP_MENU2
   ↓
4. Result: TopMenu2 START (bez PIP, fresh start)
```

**Logi:**
```
LAUNCHER_HOME: HOME button detected via onNewIntent
HOME_NAVIGATION: Other screen - navigating to TOP_MENU2/START
```

---

## Logi i debugging

### Włączanie logów HOME navigation

```bash
# Wszystkie logi HOME button
adb logcat | grep -E "(LAUNCHER_HOME|HOME_NAVIGATION|EpgDayScreen.*HOME)"

# Tylko MainActivity navigation
adb logcat | grep "HOME_NAVIGATION"

# Tylko LauncherActivity
adb logcat | grep "LAUNCHER_HOME"

# Tylko EpgDayScreen HOME logic
adb logcat | grep "EpgDayScreen.*HOME"
```

### Typowe logi - scenariusz sukcesu

**Fullscreen EPG → HOME → TopMenu2 z PIP:**
```
11-18 12:00:01.234 D LAUNCHER_HOME: HOME button detected via onNewIntent - incrementing trigger
11-18 12:00:01.235 D HOME_NAVIGATION: HOME pressed (trigger=1)
11-18 12:00:01.236 D HOME_NAVIGATION: EPG Day Test - requesting PIP navigation
11-18 12:00:01.237 D EpgDayScreen: HOME button pressed - Transferring to PIP mode and START tab
11-18 12:00:01.238 D EpgDayScreen: Player transferred to PIP - not releasing resources
```

**TopMenu2 z PIP → HOME → Fullscreen EPG:**
```
11-18 12:00:05.123 D LAUNCHER_HOME: HOME button detected via onNewIntent - incrementing trigger
11-18 12:00:05.124 D HOME_NAVIGATION: HOME pressed (trigger=2)
11-18 12:00:05.125 D HOME_NAVIGATION: PIP active - returning to fullscreen EPG
```

### Diagnoza problemów

**Problem: HOME nie działa wcale**
```bash
# Sprawdź czy aplikacja jest launcherem
adb shell dumpsys package | grep -A5 "android.intent.category.HOME"

# Sprawdź czy AccessibilityService jest włączony
adb shell settings get secure enabled_accessibility_services
```

**Problem: HOME działa ale brak PIP**
```bash
# Sprawdź czy player istnieje przed HOME
adb logcat | grep -E "(player|streamUrl|isPipMode)"

# Sprawdź czy flag jest propagowany
adb logcat | grep "shouldNavigateHomeWithPip"
```

**Problem: PIP nie zamyka się przy HOME**
```bash
# Sprawdź wykrywanie PIP state
adb logcat | grep -E "(pipPlayer|pipMode|currentScreen)"
```

---

## Znane ograniczenia

### 1. ⚠️ HOME button tylko w launcherze

**Problem:**
- `onNewIntent()` działa TYLKO gdy aplikacja jest domyślnym launcherem
- Jeśli inna aplikacja jest launcherem - HOME button nie wywołuje naszego kodu

**Rozwiązanie:**
```bash
# Ustaw BOX TV jako launcher przez ADB
adb shell cmd role add-role-holder android.app.role.HOME com.uxellence.tv.v3

# Lub przez Settings UI:
# Settings → Apps → BOX TV → Set as Home app
```

**Weryfikacja:**
```bash
# Sprawdź aktualny launcher
adb shell dumpsys package | grep "android.intent.category.HOME" -A3
```

---

### 2. ⚠️ AccessibilityService dla dodatkowej warstwy

**Status:** Opcjonalny, ale zalecany

**Problem:**
- `onNewIntent()` nie zawsze jest wywoływane na wszystkich urządzeniach
- Niektóre customowe ROMs mogą mieć zmodyfikowane zachowanie

**Rozwiązanie:** Podwójny system - onNewIntent() + AccessibilityService
```kotlin
// LauncherActivity.kt już ma BroadcastReceiver dla AccessibilityService
private val homeButtonReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HomeButtonAccessibilityService.HOME_PRESSED_ACTION) {
            homePressedTrigger.value++  // Backup trigger
        }
    }
}
```

**Włączenie:**
```bash
adb shell settings put secure enabled_accessibility_services com.uxellence.tv.v3/.HomeButtonAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

### 3. ⚠️ Player lifecycle race conditions

**Problem:**
- Player może być zwolniony przez DisposableEffect zanim PIP navigation się skończy
- Rzadkie, ale możliwe przy bardzo szybkim przełączaniu

**Rozwiązanie:** isPipMode flag
```kotlin
var isPipMode by remember { mutableStateOf(false) }

// Set BEFORE calling onNavigateToPipMode
isPipMode = true
onNavigateToPipMode(player, streamUrl)

// Check in DisposableEffect
onDispose {
    if (!isPipMode) {
        player?.release()  // Safe to release
    }
}
```

---

### 4. ⚠️ Infinite loop risk

**Problem:**
- Jeśli `onHomeNavigationComplete()` nie zostanie wywołane - flag pozostaje true
- Każda recomposition będzie ponownie wywoływała nawigację

**Rozwiązanie:** ZAWSZE wywołuj callback w LaunchedEffect
```kotlin
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        // ... navigation logic ...

        onHomeNavigationComplete()  // CRITICAL - NEVER FORGET!
    }
}
```

---

## FAQ

### Q: Czy mogę użyć tego wzorca dla innych klawiszy systemowych?

**A:** TAK, ale z ograniczeniami:
- ✅ MENU button - podobny pattern jak HOME
- ✅ BACK button - ale już obsługiwany przez onBackPressed callbacks
- ❌ VOLUME buttons - system nie pozwala przechwytywać w launcherze
- ❌ POWER button - system event, nie dostępny dla aplikacji

**Przykład dla MENU:**
```kotlin
// LauncherActivity
override fun onNewIntent(intent: Intent) {
    when {
        intent.action == Intent.ACTION_MAIN &&
        intent.hasCategory(Intent.CATEGORY_HOME) -> {
            homePressedTrigger.value++
        }
        // Dodaj obsługę MENU
        intent.action == Intent.ACTION_SEARCH -> {
            menuPressedTrigger.value++
        }
    }
}
```

---

### Q: Jak dodać nowy scenariusz nawigacji?

**A:** Rozszerz `when` w MainActivity.LaunchedEffect:

```kotlin
LaunchedEffect(homePressedTrigger) {
    if (homePressedTrigger > 0) {
        when {
            // Istniejące scenariusze
            pipPlayer != null && pipMode && currentScreen == TOP_MENU2 -> { ... }
            currentScreen == EPG_DAY -> { ... }

            // NOWY SCENARIUSZ: Search active → navigate to TopMenu2 search
            currentScreen == NavigationScreen.SEARCH -> {
                android.util.Log.d("HOME_NAVIGATION", "Search active - navigating to TopMenu2/Search")
                currentScreen = NavigationScreen.TOP_MENU2
                savedTelewizjaSection = "SEARCH"  // Custom section
            }

            else -> { ... }
        }
    }
}
```

**Zasady:**
1. Dodaj PRZED `else` - inaczej będzie unreachable
2. Użyj najbardziej specyficznego warunku na górze
3. Dodaj log dla debugging
4. Przetestuj wszystkie ścieżki

---

### Q: Jak przetestować bez fizycznego urządzenia?

**A:** Emulujesz HOME button przez ADB:

```bash
# Symuluj HOME button press
adb shell input keyevent KEYCODE_HOME

# Lub uruchom LauncherActivity bezpośrednio
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.uxellence.tv.v3/.LauncherActivity
```

**Automated testing:**
```bash
#!/bin/bash
# test_home_navigation.sh

echo "Test 1: Launch EPG"
adb shell am start -n com.uxellence.tv.v3/.LauncherActivity
sleep 2

echo "Test 2: Navigate to EPG Day"
# ... navigate via UI ...

echo "Test 3: Press HOME"
adb shell input keyevent KEYCODE_HOME
sleep 1

echo "Check logs:"
adb logcat -d | grep "HOME_NAVIGATION"
```

---

### Q: Czy state flag pattern jest lepszy od direct callbacks?

**A:** TAK, z kilku powodów:

**State flag (używane):**
```kotlin
// MainActivity
var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }

// EpgDayScreen
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        // navigation
        onHomeNavigationComplete()
    }
}
```

**Direct callback (alternatywa NIE używana):**
```kotlin
// MainActivity
EpgDayScreen(
    onHomeNavigate = {
        // Bezpośrednie wywołanie z MainActivity
        if (player != null) {
            onNavigateToPipMode(player, streamUrl)
        }
    }
)
```

**Dlaczego state flag wygrywa:**
1. ✅ **Reactive** - zmiana state automatycznie triggeruje
2. ✅ **Clean separation** - MainActivity nie zna szczegółów playera
3. ✅ **Testable** - łatwo mockować boolean flag
4. ✅ **Compose-friendly** - wykorzystuje wbudowane mechanizmy recomposition
5. ✅ **No coupling** - EpgDayScreen może mieć złożoną logikę bez wpływu na MainActivity

---

### Q: Jak dostosować opóźnienie/timing nawigacji?

**A:** Wszystkie timings są natychmiastowe, ale możesz dodać delay:

```kotlin
// W EpgDayScreen LaunchedEffect
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        // Opcjonalny delay przed nawigacją (smooth transition)
        delay(100)  // milliseconds

        isPipMode = true
        onNavigateToPipMode(player, streamUrl)
        onHomeNavigationComplete()
    }
}
```

**Kiedy używać delay:**
- 🕐 Smooth fade-out animation przed nawigacją
- 🕐 Debouncing - prevent double HOME press
- ❌ NIE używaj dla logiki biznesowej (race conditions risk)

---

### Q: Jak rollback jeśli coś pójdzie nie tak podczas nawigacji?

**A:** Dodaj error handling w LaunchedEffect:

```kotlin
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        try {
            android.util.Log.d("EpgDayScreen", "Starting HOME navigation with PIP")

            // Validation
            require(player != null) { "Player is null" }
            require(streamUrl.isNotEmpty()) { "Stream URL is empty" }

            isPipMode = true
            onNavigateToPipMode(player, streamUrl)

            android.util.Log.d("EpgDayScreen", "HOME navigation successful")
        } catch (e: Exception) {
            android.util.Log.e("EpgDayScreen", "HOME navigation failed", e)

            // Rollback
            isPipMode = false

            // Notify user (optional)
            // Toast.makeText(context, "Navigation failed", Toast.LENGTH_SHORT).show()
        } finally {
            // ALWAYS reset flag
            onHomeNavigationComplete()
        }
    }
}
```

---

## Wersje i historia zmian

### v3.12.0 (2025-11-18) - Initial implementation
**Zmiany:**
- ✅ Dodano LauncherActivity.onNewIntent() dla wykrywania HOME
- ✅ MainActivity: 3-scenariuszowa logika nawigacji
- ✅ EpgDayScreen: LaunchedEffect dla HOME navigation
- ✅ State flag pattern dla komunikacji parent-child
- ✅ Infrastructure reuse - onNavigateToPipMode z klawisza "0"

**Pliki zmodyfikowane:**
- `LauncherActivity.kt:120-134` - onNewIntent() implementation
- `MainActivity.kt:318` - shouldNavigateHomeWithPip state flag
- `MainActivity.kt:327-368` - LaunchedEffect(homePressedTrigger) logic
- `MainActivity.kt:630-631` - EpgDayScreen parameters
- `EpgDayScreen.kt:125-126` - Function signature
- `EpgDayScreen.kt:226-241` - LaunchedEffect(shouldNavigateHomeWithPip)

**Testowane scenariusze:**
- ✅ Fullscreen EPG → HOME → TopMenu2 z PIP
- ✅ TopMenu2 z PIP → HOME → Fullscreen EPG
- ✅ Netflix → HOME → TopMenu2 START

**Known issues:** Brak

---

## Podobne wzorce w projekcie

- **EXPANDABLE_CHANNELS_PATTERN.md** - Dynamic channel insertion z state flags
- **PIP_DIALOG_PATTERN.md** - Input gating + controller delegation
- **Focus Architect callbacks** - Callback delegation dla key events (CLAUDE.md)

---

## Kontakt i wsparcie

**Dokumentacja utworzona:** 2025-11-18
**Wersja aplikacji:** 3.12.0
**Status:** ✅ Production-ready

**Zgłaszanie problemów:**
- GitHub Issues (jeśli projekt ma repo)
- Internal team communication

**Przyszłe ulepszenia:**
- [ ] Animated transitions podczas HOME navigation
- [ ] Customizable HOME behavior per user settings
- [ ] Support dla MENU button (similar pattern)
- [ ] A/B testing różnych flow HOME navigation
