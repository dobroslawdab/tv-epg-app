# QUICK FIX GUIDE - App-Icons w MOJE

**Cel**: Naprawić rendering CategoryIcon dla "Moja lista kanałów" aby wyglądało jak TELEWIZJA.

---

## Problem w 30 sekund

```
MOJE "Moja lista kanałów":  ❌ Pokazuje "MO" placeholder, brak tła na focus
TELEWIZJA "Moja lista kanałów": ✅ Pokazuje "Moja lista kanałów" tekst, tło na focus

Przyczyna: isSubChannel = false (powinno być true)
Rezultat: showIcon = true (powinno być false), showBg = false (powinno być true)
```

---

## Rozwiązanie w 3 kroki

### Krok 1: Otwórz `TopMenuScreen2.kt`, linia **5536**

Znajdź:
```kotlin
val isSubChannel = channel in listOf("Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE")
```

Zmień na:
```kotlin
val isSubChannel = channel in listOf(
    "Skróty", 
    "Pojedyncze nagrania", 
    "SERIE", 
    "ZAPLANOWANE",
    "Moja lista kanałów"  // ← DODAJ TĘ LINIĘ
)
```

**Why**: To automatycznie ustawia `showIcon = false` i `showBackgroundWhenFocused = true` w CategoryIcon.

---

### Krok 2: Otwórz `TopMenuScreen2.kt`, linie **5525-5532**

Znajdź:
```kotlin
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    else -> null
}
```

Zmień na:
```kotlin
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    "Moja lista kanałów" -> null  // ← DODAJ TĘ LINIĘ
    else -> null
}
```

**Why**: Jawnie wskazuje, że "Moja lista kanałów" nie ma ikony (tekst-only).

---

### Krok 3 (OPCJONALNIE): Dodaj komentarz

Linia ~5518, zmień:
```kotlin
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
```

Na:
```kotlin
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    // ✅ CategoryIcon rendering for all channels except headers and shortcuts
    // - Sub-channels (isSubChannel=true): WIDEO style (text-only, bg on focus)
    // - App-icons ("Moja lista kanałów"): isSubChannel=true → WIDEO style
    Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
```

---

## Test

Po zmianach, sprawdź:

1. **Wygląd**: CategoryIcon "Moja lista kanałów" pokazuje pełną nazwę tekstu (nie "MO")
2. **Focus**: Przy focus pojawia się czarne tło (rgba(0,0,0,0.3))
3. **Navigation**: 
   - LEFT/RIGHT → między kartami kanałów
   - UP/DOWN → między wierszami
4. **Consistency**: Wyglądaj identycznie jak TELEWIZJA section

---

## Jeśli chcesz dodać empty placeholder (OPCJONALNIE)

Dodaj przed `LazyRow(modifier = Modifier.fillMaxWidth(), ...)` w linii ~5316:

```kotlin
} else if (isAppIcons) {
    // App Icons row - TV channels from "Moja lista kanałów"
    if (tvChannels.isEmpty()) {
        // Empty placeholder
        val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
        val isItemFocused = rowIndex == focusedRowIndex && focusedColIndex == 0

        Box(
            modifier = Modifier
                .offset(x = sx(380), y = sy(0))
                .width(sx(TELEWIZJA_CHANNEL_LIST_CARD_WIDTH))
                .height(sy(TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT))
                .clip(RoundedCornerShape(sx(12)))
                .background(Color(0xFF000000).copy(alpha = 0.1f))
                .border(
                    width = if (isItemFocused) sx(4) else 0.dp,
                    color = if (isItemFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onChannelContentFocusChange(rowIndex, 0) }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Brak kanałów",
                color = Color(0xFFEEEEEE).copy(alpha = 0.5f),
                fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        // LazyRow with channels (existing code)
        LazyRow(...)
    }
}
```

---

## Podsumowanie

| Co | Gdzie | Zmiana |
|----|-------|--------|
| Dodaj "Moja lista kanałów" | Linia 5536 | Do listy `isSubChannel` |
| Dodaj mapping dla logo | Linia 5525-5532 | `"Moja lista kanałów" → null` |
| Komentarz | Linia ~5518 | Dokumentacja |
| Empty placeholder | Linia ~5313 | OPCJONALNIE |

**Efekt**: Wygląd identyczny z TELEWIZJA, poprawna WIDEO style CategoryIcon.

---

## Pełne Szczegóły

Dla pełnej analizy, przeczytaj:
- **RAPORT_APP_ICONS_IMPLEMENTACJA.md** - Szczegółowa analiza
- **RAPORT_VISUAL_DIAGRAM.md** - Wizualne diagramy
- **FIX_MOJE_APP_ICONS_CODE.kt** - Gotowy kod do copy-paste

---

**Koniec Quick Guide**
