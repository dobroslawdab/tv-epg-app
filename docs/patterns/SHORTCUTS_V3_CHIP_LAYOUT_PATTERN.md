# Skróty v3 — Chip Layout & Y-Position Bug Fix

**Status**: ✅ Production-ready (Kino Play, 2026-05-05)
**Source**: Figma "Nowa strona główna BOX" → Kategorie (node `3061:27419`)
**Files**:
- `TopMenuScreen2.kt` — `VodShortcutsV3Row`, `CategoryChip`, `calculateVodChannelYPosition`, `handleVodNavigation`

---

## TL;DR

Migracja z dużych kart-shortcutów (235×208px z ikonami) na **tekstowe chipy 202×80px** zgodnie z Figmą. Po drodze odkryto i naprawiono **structural bug** w `calculateVodChannelYPosition` istniejący od dawna — formula odejmowała wysokość złego channela dla "above focused", co po zmniejszeniu `Skróty v3 row height` z 278 → 136 ujawniło overlap plakatów Kino Play z chipami.

---

## Specyfikacja chipa (z Figmy)

```
Width: 202px (fixed)
Height: 80px (fixed)
Padding: 16px horizontal, 24px vertical
Border radius: 16px
Background: rgba(0,0,0,0.4)
Border (default): 2px rgba(238,238,238,0.2)
Border (focused): 8px #5FEDD4 (aqua)
Gap między chipami: 24px

Typography:
- Font: Manrope Medium 24px
- Color: #EEEEEE
- Letter-spacing: 0.48
- Line-height: 32px
- Text width: max 170px, ellipsis
- Align: center
```

## Lista kategorii (11)

```kotlin
"Wszystkie" to null,                          // grid bez filtra
"Nowości 🔥" to "__NEW__",                    // sentinel — random top 10
"Akcja" to "Akcja|Action",
"Biograficzne" to "Biograficzny|Biography|Biographical",
"Disney" to "Disney",
"Dokument" to "Dokumentalny|Documentary",
"Dramat" to "Dramat|Drama",
"Familijne" to "Familijny|Family",
"Filmy polskie" to "Polski|Polish",
"Gwiezdne Wojny" to "Star Wars|Gwiezdne Wojny",
"Universal" to "Universal"
```

> Figma ma duplikat "Dramat" (12 pozycji); pominięty w implementacji.

---

## Bug w `calculateVodChannelYPosition` (linia 17737)

### Problem
Wcześniej formula iterowała `for (i in channelIndex until focusedRowIndex - 2)` ale brała `betweenChannelName = channels.getOrNull(i + 1)`. Dla aktualnego kanału "above focused", to **omijało** własną wysokość kanału i zamiast niej brało wysokość kanału focused.

Maskowane przez stare wartości (Skróty=278) bo Kino Play=346 i Skróty=278 dawały podobne sumy. Po zmianie Skróty 278 → 136, overlap stał się ewidentny.

### Symptom
Gdy chip focused (Skróty Y=340):
- ❌ Kino Play Y = 340 - **136** (Skróty height) = 204 → plakaty wystawały na chipy
- ✅ Po naprawie: Y = 340 - **346** (Kino Play own height) = -6 (scrolled off-screen)

### Fix
```kotlin
// PRZED (bug):
for (i in channelIndex until focusedRowIndex - 2) {
    val betweenChannelName = channels.getOrNull(i + 1) ?: ""  // ← bug
    ...
}

// PO (poprawne):
for (i in channelIndex until focusedRowIndex - 2) {
    val betweenChannelName = channels.getOrNull(i) ?: ""      // ← fix
    ...
}
```

Range `[channelIndex, focusedRowIndex-2)` to **stack channels above focused INCLUSIVE of current**. Każdy z nich kontrybuuje swoją wysokością do offsetu w górę.

---

## Constanty wysokości rzędu

```kotlin
private const val ODKRYWAJ_SHORTCUTS_V3_NORMAL_ROW_HEIGHT = 136  // chip 80px + 56px spacing dół
private const val ODKRYWAJ_SHORTCUTS_V3_EXPANDED_ROW_HEIGHT = 136 // NO expansion
```

Plus hardcoded `136` (5 miejsc w `calculateVodChannelYPosition`) zastąpione poprzednie `278`.

**Spacing 56px** osiągany przez: row height 136 - chip 80 = 56 (po dolnej krawędzi chipa).

---

## Anti-patterns (czego NIE robić)

### 1. NIE używaj `defaultMinSize`/`requiredHeight` na chipie w LazyRow

Próbowane w trakcie debug:
- `Modifier.defaultMinSize(minHeight = sy(80))` — ignorowane przez LazyRow constraints
- `Modifier.requiredHeight(sy(80))` — dało nieprzewidywalne wyniki
- `Modifier.height(sy(80))` jako pierwszy modifier — nie wpłynęło widocznie

**Działa**: `Modifier.size(sx(202), sy(80))` jako pierwszy modifier — fixed dimensions z Figmy.

### 2. NIE pomijaj `lazyListState` w propagacji

Bez:
```kotlin
LazyRow(
    state = lazyListState,  // ← MUSI BYĆ
    ...
)
```

Auto-reset scrolla z parent (`LaunchedEffect` w `VodWithChannels:12976`) **NIE** zadziała — chipy zostaną na ostatniej pozycji X po opuszczeniu rzędu.

### 3. NIE ustawiaj sztucznego `effectiveFocusedRowIndex`

Próbowano "zachować layout" gdy chip focused — udawanie że focus jest na poprzednim channelu. To łamie standardową regułę "focused channel zawsze na Y=340" i zostawia overlap. Lepiej naprawić root cause (formula bug).

### 4. NIE usuwaj `extraSpacing` warunkowo dla Skróty v3

Pierwotnie próbowane: `if (focusedColIndex >= 0 && !focusedIsShortcutsV3) VOD_CONTENT_FOCUS_EXTRA_SPACING else 0`. Niepotrzebne po naprawie formuły.

### 5. NIE używaj hardcoded `5` jako limit kolumn dla shortcutów

`handleVodNavigation` miał `if (focusedColIndex < 5)` (z czasów 6 ShortcutCardów). Zmieniaj na dynamic:
```kotlin
val maxColIndex = (channelFocusRequesters.keys
    .filter { it.first == focusedRowIndex }
    .maxOfOrNull { it.second } ?: 0)
if (focusedColIndex < maxColIndex) { ... }
```

---

## Implementation Pattern

```kotlin
@Composable
private fun CategoryChip(
    label: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val borderColor = if (isFocused) Color(0xFF5FEDD4) else Color(0x33EEEEEE)
    val borderWidth = if (isFocused) sx(8) else sx(2)
    val shape = RoundedCornerShape(sx(16))

    Box(
        modifier = Modifier
            .size(sx(202), sy(80))                                // Figma: fixed
            .clip(shape)
            .background(Color(0x66000000))
            .border(width = borderWidth, color = borderColor, shape = shape)
            .padding(horizontal = sx(16), vertical = sy(24))
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else false
            }
            .onFocusChanged { onFocusChange(it.isFocused) }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color(0xFFEEEEEE),
            fontSize = (24 * sy(1).value).sp,
            fontFamily = ManropeFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.48.sp,
            lineHeight = (32 * sy(1).value).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = sx(170))
        )
    }
}
```

LazyRow:
```kotlin
LazyRow(
    state = lazyListState,                                         // PROPAGUJ z parent
    modifier = Modifier
        .fillMaxWidth()
        .height(sy(80)),
    contentPadding = PaddingValues(start = sx(80), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(24)),
    verticalAlignment = Alignment.CenterVertically
) {
    itemsIndexed(categories) { index, (label, filter) -> ... }
}
```

---

## Verification (manualna na TV)

1. **Layout stabilny gdy fokus na Kino Play**:
   - Kino Play CategoryIcon na Y=340 (focused)
   - Plakaty Kino Play widoczne
   - Skróty chipy widoczne poniżej (Y=686)
   - 56px spacing chip↑ do Polecane CategoryIcon

2. **Layout scrolluje wyżej gdy fokus na chipie**:
   - Strona scroll wyżej, Kino Play znika off-screen top
   - Skróty chipy na Y=340 (focused)
   - Brak overlap z Kino Play
   - Polecane na Y=476 (56px pod chipami)

3. **Auto-reset scrolla X**:
   - Scrolluj chipy w prawo (RIGHT 5×)
   - Zejdź DOWN do Polecane
   - Wróć UP do chipów
   - Powinno być scroll do "Wszystkie" (col 0)

4. **Nawigacja przez wszystkie 11 chipów**:
   - RIGHT 10× od "Wszystkie" → "Universal" (focused)
   - 11. RIGHT zatrzymuje się (last col)

---

## Powiązane file:line

| Plik:Linia | Co |
|---|---|
| `TopMenuScreen2.kt:8762-8763` | Constanty `ODKRYWAJ_SHORTCUTS_V3_*_ROW_HEIGHT = 136` |
| `TopMenuScreen2.kt:12880` | `channels` lista (z "Skróty v3") |
| `TopMenuScreen2.kt:12942-12946` | `repeat(11)` dla 11 FocusRequesters chipów |
| `TopMenuScreen2.kt:12976+` | `LaunchedEffect` auto-reset scrolla — wymaga `lazyListState` w children |
| `TopMenuScreen2.kt:17263+` | `VodShortcutsV3Row` composable |
| `TopMenuScreen2.kt:17354+` | `CategoryChip` composable |
| `TopMenuScreen2.kt:17698, 17704, 17720, 17742, 17757, 17763, 17777` | Wszystkie hardcoded `136` w `calculateVodChannelYPosition` |
| `TopMenuScreen2.kt:17737` | **FIX**: `channels.getOrNull(i)` zamiast `channels.getOrNull(i + 1)` |
| `TopMenuScreen2.kt:17963+` | Dynamic `maxColIndex` dla RIGHT navigation w shortcuts |

---

## Lessons learned

1. **Pre-existing bugs maskowane przez stare wartości** — `i+1` w formula niepoprawne, ale 278 (Skróty old) ≈ 346 (vertical channel) sprawiało że wyniki były "plus-minus poprawne". Po zmianie 278 → 136, gap nie do ukrycia.

2. **Compose constraints w LazyRow + density 320** wymagają `size(sx, sy)` jako pierwszy modifier. `defaultMinSize` i `requiredHeight` zachowują się nieprzewidywalnie.

3. **Auto-reset patterns wymagają explicit propagacji `lazyListState`** — `LaunchedEffect` w parent nie dotyka children które tworzą własny `LazyListState()`.

4. **Sub-channel hardcoded indices** (jak `< 5` w nav) odbywają się od starych wartości — po dodaniu/usunięciu pozycji łatwo o subtle bug. Dynamic calc z `channelFocusRequesters.keys.maxOfOrNull` jest robust.

5. **Skill `layout-engineer`** definiuje OUTPUT FORMAT (Analiza Input → Kod → Verification Checklist) — czytaj **przed** implementacją, nie po.

---

**Last updated**: 2026-05-05 — fix in production na real TV (192.168.31.112).
