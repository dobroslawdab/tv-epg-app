// ============================================================================
// FILE: FIX_MOJE_APP_ICONS_CODE.kt
// ============================================================================
// Gotowy kod do implementacji zmian w TopMenuScreen2.kt
// Dodaj te fragmenty w odpowiednich miejscach
//
// INSTRUKCJA:
// 1. Otwórz TopMenuScreen2.kt
// 2. Zastosuj kolejno ZMIANĘ #1, #2, #3, #4
// 3. Test navigation i focus
// ============================================================================

// ============================================================================
// ZMIANA #1: Linia 5536
// ============================================================================
// PRZED:
// val isSubChannel = channel in listOf("Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE")

// PO:
val isSubChannel = channel in listOf(
    "Skróty", 
    "Pojedyncze nagrania", 
    "SERIE", 
    "ZAPLANOWANE",
    "Moja lista kanałów"  // ✅ NOWE: app-icons channel potrzebuje WIDEO style
)

// UZASADNIENIE:
// isSubChannel = true powoduje:
// - showIcon = false (tekst-only, bez ikony)
// - showBackgroundWhenFocused = true (czarne tło przy focus)
// To automatycznie zapewnia WIDEO style dla CategoryIcon "Moja lista kanałów"


// ============================================================================
// ZMIANA #2: Linie 5525-5532
// ============================================================================
// PRZED:
/*
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    else -> null
}
*/

// PO:
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    "Moja lista kanałów" -> null  // ✅ NOWE: app-icons bez ikony (tekst-only)
    else -> null
}

// UZASADNIENIE:
// Jawnie wskazuje, że "Moja lista kanałów" to app-icons (bez ikony)
// Razem ze zmianą #1, to ustawia showIcon = false w CategoryIcon


// ============================================================================
// ZMIANA #3: Linia 5518 - Dodaj komentarz
// ============================================================================
// PRZED:
/*
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
*/

// PO:
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    // ✅ CategoryIcon rendering for all channels except headers and shortcuts
    // - For sub-channels (isSubChannel=true): WIDEO style (text-only, bg on focus)
    // - For regular channels: normal style with icon
    // - For app-icons ("Moja lista kanałów"): isSubChannel=true → WIDEO style
    Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
}

// UZASADNIENIE:
// Komentarz wyjaśnia logikę dla przyszłych zmian


// ============================================================================
// ZMIANA #4 (OPCJONALNA): Linia 5313
// ============================================================================
// Dodaj empty placeholder dla consistency z TELEWIZJA
// UMIEŚĆ PRZED isAppIcons check w else if

// PRZED:
/*
} else if (isAppIcons) {
    // App Icons row - TV channels from "Moja lista kanałów"
    LazyRow(
*/

// PO:
} else if (isAppIcons) {
    // App Icons row - TV channels from "Moja lista kanałów"
    // IDENTICAL to TELEWIZJA implementation (ChannelListCard, direct focus, 12px spacing)
    if (tvChannels.isEmpty()) {
        // ✅ Empty content placeholder - attach FocusRequester to prevent crash
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
        LazyRow(
            // ... rest of existing code (unchanged)
        )
    }
}

// UZASADNIENIE:
// - Consistency z TELEWIZJA section (linia 8313-8341)
// - Zapobiega crash przy kliknięciu "Moja lista kanałów" gdy tvChannels.isEmpty()
// - Pokazuje "Brak kanałów" zamiast pustego LazyRow
// - OPCJONALNE: Jeśli tvChannels jest zawsze populate, pomiń tę zmianę


// ============================================================================
// SUMMARY OF CHANGES
// ============================================================================
// 
// Mandatory:
// ✅ Change #1 (Line 5536): Add "Moja lista kanałów" to isSubChannel
// ✅ Change #2 (Lines 5525-5532): Add mapping for "Moja lista kanałów"
// ✅ Change #3 (Line 5518): Add documentation comment
//
// Optional:
// ⚠️ Change #4 (Line 5313): Add empty placeholder (recommended for robustness)
//
// ============================================================================
// VERIFICATION CHECKLIST
// ============================================================================
//
// After implementation:
// - [ ] CategoryIcon for "Moja lista kanałów" renders without icon (text-only)
// - [ ] CategoryIcon shows black background on focus (rgba(0,0,0,0.3))
// - [ ] CategoryIcon shows aqua border on focus (#5AECD3, 6px)
// - [ ] Can focus on CategoryIcon (focusedColIndex == -1)
// - [ ] LEFT/RIGHT navigates between channel cards
// - [ ] UP/DOWN navigates between rows
// - [ ] Empty state shows placeholder (if tvChannels.isEmpty()) - if Change #4 applied
// - [ ] Visual appearance matches TELEWIZJA app-icons
//
// ============================================================================
