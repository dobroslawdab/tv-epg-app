# 🔗 Framelink Figma MCP - Integration Guide

**Purpose**: Automatyczne pobieranie specs z Figmy przez API (bez ręcznego przepisywania)
**Status**: MCP zainstalowane ✅ - gotowe do użycia gdy będziesz miał File Key
**Last Update**: 2025-10-03

---

## ✅ CO MASZ JUŻ ZAINSTALOWANE

Framelink Figma MCP to narzędzie, które pozwala Claude Code automatycznie łączyć się z Figma API.

**Dostępne funkcje (widoczne w `claude code --help`):**
- `mcp__figma-dev-mode__get_figma_data` - pobierz specs komponentu
- `mcp__figma-dev-mode__download_figma_images` - ściągnij obrazy/ikony

**Zaleta**: Nie musisz ręcznie przepisywać specs - Framelink robi to za Ciebie.

---

## 📋 CO POTRZEBUJESZ

### 1. Figma File Key

**Gdzie znaleźć:**

Otwórz swój projekt w Figmie w przeglądarce:

```
https://www.figma.com/design/ABC123XYZ/TV-Components?node-id=123:456
                              ^^^^^^^^^
                              To jest File Key
```

**Przykład:**
- URL: `https://www.figma.com/design/K7vH3mP9qR2sT1wU4xY5z/TV-App`
- File Key: `K7vH3mP9qR2sT1wU4xY5z`

### 2. Node ID (opcjonalnie - dla konkretnego komponentu)

**Gdzie znaleźć:**

W Figmie:
1. Kliknij na component (np. CategoryIcon)
2. URL się zmieni:
   ```
   https://www.figma.com/design/ABC123XYZ/TV-Components?node-id=789:012
                                                               ^^^^^^^
                                                               Node ID
   ```

Lub:
1. Kliknij prawym na component → Copy link
2. Link zawiera `node-id=...`

**Przykład:**
- CategoryIcon: `node-id=123:456`
- MiniatureCard: `node-id=234:567`

### 3. Figma Access Token (jeśli potrzebne)

Framelink może wymagać Figma Personal Access Token do autoryzacji.

**Jak uzyskać:**
1. Idź do Figma → Settings → Account → Personal Access Tokens
2. Generate new token
3. Skopiuj token (pokazuje się tylko raz!)
4. Dodaj do zmiennych środowiskowych:
   ```bash
   export FIGMA_ACCESS_TOKEN="figd_YOUR_TOKEN_HERE"
   ```

**Uwaga**: Nie commituj tokena do repo! Dodaj do `.env` lub `.gitignore`.

---

## 🚀 JAK UŻYWAĆ FRAMELINK

### Scenariusz 1: Pobierz Specs Konkretnego Komponentu

```bash
cd /Users/uxellenceuxe/TV_componenty

claude code \
  -f docs/agents/layout-engineer.md \
  -f docs/figma/design-system.md \
  --tool figma \
  --figma-file "ABC123XYZ" \
  --figma-node "789:012" \
  -p "Layout Engineer: Convert this component to Jetpack Compose.
      Use sx/sy scaling according to design system."
```

**Co się dzieje:**
1. Claude Code wywołuje `mcp__figma-dev-mode__get_figma_data`
2. Framelink łączy się z Figma API
3. Pobiera JSON z danymi komponentu:
   ```json
   {
     "name": "CategoryIcon",
     "width": 216,
     "height": 216,
     "fills": [{"color": "#2C2C2C"}],
     "cornerRadius": 12,
     "strokes": [{"color": "#5AECD3", "weight": 4}]
   }
   ```
4. Layout Engineer widzi te dane i generuje kod Compose

**Output:**
```kotlin
/**
 * CategoryIcon - Converted from Figma
 * Node ID: 789:012
 * Figma specs: 216x216px, radius 12px, border 4px #5AECD3 when focused
 */
@Composable
fun CategoryIcon(
    isFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .size(sx(216), sy(216))            // Figma: 216x216
            .clip(RoundedCornerShape(sx(12)))  // Figma: radius=12
            .background(Color(0xFF2C2C2C))     // Figma: fill=#2C2C2C
            .border(
                width = if (isFocused) sx(4) else 0.dp,  // Figma: stroke=4px
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
            )
    )
}
```

---

### Scenariusz 2: Pobierz Cały Frame (Multiple Components)

```bash
# Pobierz specs całego frame'a z wieloma komponentami
claude code \
  -f docs/agents/layout-engineer.md \
  --tool figma \
  --figma-file "ABC123XYZ" \
  --figma-node "100:200" \
  --depth 2 \
  -p "Layout Engineer: Convert all components in this frame to Compose.
      Create separate @Composable for each child component."
```

**Parametr `--depth`**: Ile poziomów w dół hierarchii pobrać (default=1)

---

### Scenariusz 3: Pobierz Tylko Layout Info (Bez Szczegółów)

```bash
# Szybki overview struktury
claude code \
  --tool figma \
  --figma-file "ABC123XYZ" \
  --figma-node "100:200" \
  -p "Show me the layout structure of this frame (component names and sizes)"
```

**Użyj gdy:** Chcesz zobaczyć co jest w frame, zanim zaczniesz konwertować.

---

### Scenariusz 4: Pobierz Obrazy/Ikony

```bash
# Ściągnij wszystkie ikony z frame'a do projektu
claude code \
  --tool figma \
  --figma-file "ABC123XYZ" \
  --figma-node "500:600" \
  -p "Download all images from this node to
      /Users/uxellenceuxe/TV_componenty/app/src/main/res/drawable-xhdpi/
      as PNG @2x"
```

**Co się dzieje:**
1. Framelink identyfikuje wszystkie image nodes
2. Exportuje jako PNG @2x (dla Android xhdpi)
3. Zapisuje w podanej lokalizacji z nazwami z Figmy

**Alternatywnie (manual control):**
```bash
# Ściągnij konkretny obrazek
claude code \
  --tool figma \
  --figma-file "ABC123XYZ" \
  --figma-node "icon-netflix" \
  -p "Download this icon as PNG 200x200, save to
      app/src/main/res/drawable-xhdpi/imgi_netflix_2x.png"
```

---

## 📊 CO FRAMELINK POTRAFI WYCIĄGNĄĆ

### Layout & Dimensions
- ✅ Width, Height (px)
- ✅ X, Y position (relative to parent)
- ✅ Padding, Margin
- ✅ Gap between children (AutoLayout)

### Styling
- ✅ Background color (#HEX)
- ✅ Border (width, color)
- ✅ Corner radius
- ✅ Shadow/Elevation
- ✅ Opacity

### Typography
- ✅ Font size (px)
- ✅ Font weight
- ✅ Line height
- ✅ Letter spacing
- ✅ Text color
- ✅ Alignment

### Images
- ✅ Image fills (URLs)
- ✅ Icon references
- ✅ Export as PNG/SVG

### Constraints & AutoLayout
- ✅ Flex direction (Row/Column)
- ✅ Alignment
- ✅ Spacing mode
- ✅ Constraints (fixed, fill, hug)

---

## ⚠️ CZEGO FRAMELINK NIE POTRAFI

- ❌ **Animacje** - Figma nie eksportuje animation timing przez API (musisz ręcznie)
- ❌ **Interactive Prototypes** - Framelink nie rozumie prototype flows
- ❌ **Components Instances Logic** - Dostaniesz wartości, ale nie logikę variants
- ❌ **Complex Gradients** - Może być nieprecyzyjnie dla złożonych gradientów
- ❌ **Video/Lottie** - Tylko static images

**Rozwiązanie**: Uzupełnij manual specs w `component-specs.md` dla tych aspektów.

---

## 🔍 DEBUGGING

### Problem: "Figma file not found"

**Przyczyna**: Nieprawidłowy File Key lub brak dostępu.

**Rozwiązanie:**
1. Sprawdź File Key - skopiuj z URL Figmy
2. Upewnij się, że masz dostęp do pliku (owner/editor/viewer)
3. Jeśli private file - dodaj Figma Access Token (patrz wyżej)

### Problem: "Node not found"

**Przyczyna**: Nieprawidłowy Node ID lub node usunięty.

**Rozwiązanie:**
1. Sprawdź Node ID - kliknij prawym → Copy link → sprawdź `node-id=...`
2. Upewnij się, że node nadal istnieje w pliku
3. Spróbuj bez `--figma-node` (pobierze cały plik)

### Problem: "Missing Figma Access Token"

**Przyczyna**: Framelink wymaga autoryzacji dla tego pliku.

**Rozwiązanie:**
```bash
# Wygeneruj token w Figma Settings
export FIGMA_ACCESS_TOKEN="figd_YOUR_TOKEN"

# Spróbuj ponownie
claude code --tool figma ...
```

### Problem: "Too many requests"

**Przyczyna**: Rate limit Figma API (zazwyczaj ~100 requests/minute).

**Rozwiązanie:**
1. Poczekaj 1 minutę
2. Ogranicz ilość requests - użyj cache lokalnie
3. Pobierz większy fragment za raz (wyższy `--depth`) zamiast wiele małych

---

## 💡 BEST PRACTICES

### 1. Cache Results Locally

Gdy pobierzesz specs z Figmy, zapisz je manual w `component-specs.md`:

```bash
# Pobierz z Figmy
claude code --tool figma ... > /tmp/figma-output.txt

# Przeczytaj output, dodaj do component-specs.md manual
# Teraz masz backup - nie musisz pobierać za każdym razem
```

### 2. Use Depth Wisely

```bash
# ❌ Źle - depth=5 dla całego designu (długo trwa, dużo danych)
--depth 5

# ✅ Dobrze - depth=1 dla single component
--depth 1

# ✅ Dobrze - depth=2 dla frame z kilkoma komponentami
--depth 2
```

### 3. Name Conventions in Figma

Nadawaj sensowne nazwy w Figmie - Framelink używa ich:

```
✅ Dobrze:
- CategoryIcon
- MiniatureCard
- TvChannelLogo

❌ Źle:
- Rectangle 123
- Frame 45
- Group 7
```

### 4. Combine with Manual Specs

Framelink + manual specs = najlepsza kombinacja:

```bash
# 1. Pobierz base specs z Figmy
claude code --tool figma ... -p "Extract layout and dimensions"

# 2. Uzupełnij animations, behaviors ręcznie w component-specs.md

# 3. Wywołaj Layout Engineer z oboma źródłami
claude code \
  -f docs/agents/layout-engineer.md \
  -f docs/figma/component-specs.md \  # Manual specs
  --tool figma \                       # Auto specs
  --figma-file "..." \
  -p "Build component using both sources"
```

---

## 🎓 PRZYKŁADOWY WORKFLOW

### Start to Finish: Nowy Component z Figmy

```bash
# KROK 1: Sprawdź co jest w frame
claude code \
  --tool figma \
  --figma-file "YOUR_FILE_KEY" \
  --figma-node "FRAME_NODE_ID" \
  -p "List all components in this frame with their sizes"

# OUTPUT: Lista komponentów, np:
# - HighlightCard: 400x225px
# - Badge: 80x24px
# - PlayButton: 60x60px

# KROK 2: Wybierz component, pobierz pełne specs
claude code \
  --tool figma \
  --figma-file "YOUR_FILE_KEY" \
  --figma-node "HIGHLIGHTCARD_NODE_ID" \
  -p "Extract complete specs for HighlightCard:
      - Dimensions, position, styling
      - Colors (hex codes)
      - Typography if text present
      - Border radius, padding
      - Focus states (if multiple variants)"

# OUTPUT: Pełne specs w formacie JSON/text

# KROK 3: Zapisz manual (optional backup)
nano docs/figma/component-specs.md
# [wklej specs do template]

# KROK 4: Konwertuj na Compose
claude code \
  -f docs/agents/layout-engineer.md \
  -f docs/figma/design-system.md \
  -f docs/figma/component-specs.md \
  --tool figma \
  --figma-file "YOUR_FILE_KEY" \
  --figma-node "HIGHLIGHTCARD_NODE_ID" \
  -p "Layout Engineer: Convert HighlightCard to Compose.
      Use sx/sy scaling, follow design system.
      Add focus states (4px aqua border).
      Include FocusRequester and focusable() setup."

# OUTPUT: Complete Compose code ready to use

# KROK 5: Verify & Test
# Skopiuj kod do TopMenuScreen2.kt lub odpowiedniego pliku
# Build & test na urządzeniu
```

---

## 🔗 RELATED FILES

- **Agent**: `docs/agents/layout-engineer.md` - jak Layout Engineer używa specs
- **Design System**: `docs/figma/design-system.md` - wartości do użycia w konwersji
- **Manual Specs**: `docs/figma/component-specs.md` - backup/uzupełnienie Framelink
- **Workflow**: `docs/workflows/figma-to-compose.md` - porównanie Framelink vs Manual

---

## 🎯 QUICK REFERENCE

### Pobierz Specs Komponentu
```bash
claude code --tool figma \
  --figma-file "FILE_KEY" \
  --figma-node "NODE_ID" \
  -p "Extract specs for conversion to Compose"
```

### Konwertuj Component na Compose
```bash
claude code \
  -f docs/agents/layout-engineer.md \
  --tool figma \
  --figma-file "FILE_KEY" \
  --figma-node "NODE_ID" \
  -p "Layout Engineer: Convert to Compose with sx/sy"
```

### Pobierz Obrazy
```bash
claude code --tool figma \
  --figma-file "FILE_KEY" \
  --figma-node "ICON_NODE_ID" \
  -p "Download as PNG to app/src/main/res/drawable-xhdpi/"
```

---

**END OF FRAMELINK GUIDE**

Last updated: 2025-10-03

**Status**: Gotowe do użycia gdy będziesz miał File Key z Figmy.
