# Thumbnail Context Menu Pattern (long-press na kaflu)

**Status**: ✅ Produkcyjnie w gridzie Kino Play (KINO_GRID)
**Wprowadzono**: v5.12.0
**Pliki**: `components/ThumbnailContextMenu.kt` (nowy), `components/VerticalVodCard.kt`, `KinoGridScreen.kt`

Floating menu kontekstowe otwierane **długim przytrzymaniem OK** na zfokusowanym kaflu, renderowane nad całym UI, z karetką wskazującą kafel. Design wg Figmy (TV Platforms Play 4039-3695/3868). Docelowo reużywalne na wszystkich typach miniaturek — komponent jest **data-driven**.

## Architektura (3 elementy)

### 1. Detekcja long-press na kaflu — `VerticalVodCard`
Wzorzec z `search/SearchScreen.kt`: `event.nativeKeyEvent.repeatCount > 0` = przytrzymanie. `adb shell input keyevent --longpress 23` generuje to samo (powtarzane ACTION_DOWN), więc jest testowalne.

Handler OK (Enter/NumPadEnter/DirectionCenter) — **klik na KeyUp**, long-press na repeacie:
```kotlin
var longPressHandled by remember { mutableStateOf(false) }
var pressActive by remember { mutableStateOf(false) }  // naciśnięcie zaczęło się NA tym kaflu
when {
    event.type == KeyDown -> {
        if (repeatCount == 0) { pressActive = true; longPressHandled = false }
        else if (!longPressHandled) { longPressHandled = true; onLongPress() }
        true
    }
    event.type == KeyUp -> {
        if (pressActive && !longPressHandled) onClick()  // krótki = detal
        pressActive = false; longPressHandled = false; true
    }
}
```
**Pułapki (rozwiązane):**
- **KeyUp-leak na wejściu**: bez `pressActive` KeyUp od OK, którym wszedłeś na grid, odpalał `onClick` na świeżo zfokusowanym kaflu → auto-otwierał detal. Klik tylko gdy KeyDown(repeatCount==0) wystąpił **na tym kaflu**.
- Long-press **nie** może odpalić `onClick` — dlatego klik jest na KeyUp, a long-press ustawia `longPressHandled`.

### 2. Floating menu — `ThumbnailContextMenu`
`data class ThumbnailMenuItem(label, destructive, onClick)` — lista data-driven.
- Pełnoekranowy `Box` `zIndex(210f)` (nad FullScreenPicker=200), `focusRequester`+`focusable`, własne `onKeyEvent`.
- **Fokus łapany NATYCHMIAST** (`requestFocus()` w `LaunchedEffect`, **bez `clearFocus`**) — inaczej trzymany OK przeciekał w luce bez fokusa na chipy Sortuj/Kategoria. Gating `inputEnabled` (delay ~300ms) ignoruje resztki z otwarcia.
- **BACK przez `BackHandler`** (`androidx.activity.compose.BackHandler { onDismiss() }`) — `onKeyEvent` nie zawsze łapie BACK (na części urządzeń idzie wprost do `onBackPressed`), co **zamykało apkę** zamiast menu.
- Wybór OK tylko przy `repeatCount==0` — trzymanie z otwarcia **nie** aktywuje 1. pozycji.
- Klawisze: UP/DOWN = pozycje menu; **LEFT/RIGHT = `onNavigate(±1)`** (przejście po kaflach gridu, menu zostaje); OK = wybór+zamknięcie; BACK = zamknięcie. Wszystko inne połknięte (`true`).
- Wygląd: karetka (Canvas, trójkąt) na `caretCenterX`; body `#EEEEEE` `RoundedCornerShape`; item zfokusowany = aqua pill `#5FEDD4`, tekst purple `#48227C`, destructive `#DD1538`; chevron ⌄ gdy `items.size > maxVisible` (scroll).

### 3. Wpięcie w grid — `KinoGridScreen`
- Stan `var contextMenuItem by remember { mutableStateOf<VodContent?>(null) }`.
- `VerticalVodCard(onLongPress = { focusedRow=row; focusedCol=col; contextMenuItem=vodContent })`.
- **Guard** na początku key-handlera Box gridu: `if (contextMenuItem != null) return@onPreviewKeyEvent false` (menu ma fokus i samo łapie klawisze).
- Render menu jako ostatnie dziecko Box.
- **Kotwiczenie z metryk gridu** (NIE z LazyGrid internals): `cellW=(1920-padL-padR-(cols-1)*gap)/cols`, `cardCenterPx = padL + focusedCol*(cellW+gap) + cellW/2`, `anchorX=(cardCenter-menuW/2).coerceIn(...)`, `caretCenterX=cardCenter-anchorX`. Y stały pod przypiętym wierszem.
- `onNavigate(dx)`: `newIndex=(row*cols+col+dx).coerceIn(0,last)`; ustaw `focusedRow/Col` + `contextMenuItem` → kotwica i pozycje przeliczają się same, menu zostaje otwarte.
- `onDismiss`: **najpierw** `gridFocusRequesters[Pair(focusedRow,focusedCol)]?.requestFocus()`, **potem** `contextMenuItem=null` — fokus wraca na kafel bez mignięcia (brak luki bez fokusa, bez `clearFocus`, bez `delay`).

## Pozycje (Kino Play)
Budowane przy call-site z `contextMenuItem`: **Oglądaj** (→`onMovieClicked`), **Dodaj/Usuń z Mojej listy** (`WatchlistManager.toggle`, label wg `WatchlistManager.contains`), **Wypożycz** (atrapa/Toast — do podpięcia RentalManager), **Więcej informacji** (→`onMovieClicked`).

## Sterowanie (pilot TV)
przytrzymaj OK = menu · góra/dół = pozycje · lewo/prawo = kolejny/poprzedni kafel · OK = wybór · BACK = zamknij.

## Rozszerzanie na inne miniaturki
1. Dodaj `onLongPress` do komponentu kafla (analogicznie do VerticalVodCard — `pressActive` + `longPressHandled`).
2. W ekranie: stan `contextMenuItem`, guard w key-handlerze, render `ThumbnailContextMenu` z policzoną kotwicą i listą `ThumbnailMenuItem` właściwą dla typu treści.
3. Reużyj `onNavigate`/`onDismiss` wg wzorca powyżej.

## Lekcje
- **BACK w Compose overlay = `BackHandler`**, nie `onKeyEvent` (inaczej apka wychodzi).
- **Łap fokus natychmiast, nie czyść go** — `clearFocus` + opóźniony `requestFocus` = przeciek klawiszy w luce + mignięcie przy zamknięciu.
- **Long-press = `repeatCount>0`** (wzorzec z SearchScreen, testowalny `--longpress`); klik przenieś na KeyUp z flagą `pressActive`, by wejście na ekran nie odpalało akcji.
