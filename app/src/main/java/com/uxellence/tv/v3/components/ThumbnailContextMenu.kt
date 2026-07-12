package com.uxellence.tv.v3.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** Pozycja menu kontekstowego (data-driven — reużywalne dla różnych typów miniaturek). */
data class ThumbnailMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

private val MENU_BG = Color(0xFFEEEEEE)
private val MENU_TEXT = Color(0xFF48227C)        // purple_basic
private val MENU_TEXT_DESTRUCTIVE = Color(0xFFDD1538) // red_base
private val MENU_FOCUS_PILL = Color(0xFF5FEDD4)  // aqua / container focused

/**
 * Floating menu kontekstowe nad miniaturką (wg Figmy TV Platforms Play 4039-3695/3868).
 * Karetka u góry wskazuje kafel, jasne tło, item zfokusowany = aqua pill, destructive = czerwony,
 * chevron ⌄ gdy lista się nie mieści. Otwierane długim przytrzymaniem OK na kaflu.
 *
 * Nawigacja: UP/DOWN zmienia pozycję, OK wykonuje akcję i zamyka, BACK zamyka.
 * Wszystkie klawisze są konsumowane (nie przeciekają do gridu pod spodem).
 *
 * @param anchorX lewy X popupu, @param anchorTopY górny Y (pod kaflem),
 * @param caretCenterX środek karetki względem lewej krawędzi popupu,
 * @param menuWidth szerokość popupu.
 */
@Composable
fun ThumbnailContextMenu(
    items: List<ThumbnailMenuItem>,
    anchorX: Dp,
    anchorTopY: Dp,
    caretCenterX: Dp,
    menuWidth: Dp,
    onDismiss: () -> Unit,
    onNavigate: (dx: Int) -> Unit = {},  // lewo/prawo: przejście po kaflach gridu (menu zostaje)
    // false = menu pozycjonowane OBOK kafla (np. wiersze na zakładce Kino Play,
    // gdzie pod plakatem brak miejsca) — karetka wskazywałaby pustkę
    showCaret: Boolean = true,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    var focusedIndex by remember { mutableStateOf(0) }
    var inputEnabled by remember { mutableStateOf(false) }  // gating — KeyUp z otwarcia nie klika
    val menuFocusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val maxVisible = 7  // powyżej tylu pozycji pokazujemy chevron + scroll

    // BACK zamyka menu (a NIE wychodzi z apki). BackHandler przechwytuje wstecz przez
    // OnBackPressedDispatcher — działa niezależnie od tego, czy fokus jest na menu i czy
    // onKeyEvent złapie BACK (na części urządzeń BACK idzie wprost do onBackPressed).
    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(210f)  // nad FullScreenPicker (200) i resztą UI
            .focusRequester(menuFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (!inputEnabled) return@onKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent true
                when (event.key) {
                    Key.DirectionUp -> {
                        if (focusedIndex > 0) focusedIndex--
                        true
                    }
                    Key.DirectionDown -> {
                        if (focusedIndex < items.lastIndex) focusedIndex++
                        true
                    }
                    Key.DirectionLeft -> {
                        // tylko świeże naciśnięcie — żeby trzymanie nie przeskakiwało lawinowo
                        if (event.nativeKeyEvent.repeatCount == 0) onNavigate(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        if (event.nativeKeyEvent.repeatCount == 0) onNavigate(+1)
                        true
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        // Tylko świeże naciśnięcie (repeatCount==0) — NIE trzymanie z otwarcia
                        // menu, żeby przytrzymany OK nie wybrał od razu 1. pozycji.
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            val item = items.getOrNull(focusedIndex)
                            onDismiss()
                            item?.onClick?.invoke()
                        }
                        true
                    }
                    Key.Back, Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> true  // połknij wszystko inne — nic nie idzie do gridu
                }
            }
    ) {
        Column(
            modifier = Modifier
                .offset(x = anchorX, y = anchorTopY)
                .width(menuWidth)
        ) {
            // Karetka wskazująca kafel (trójkąt ostrzem do góry)
            val caretW = sx(28)
            val caretH = sy(14)
            if (showCaret) Box(modifier = Modifier.fillMaxWidth().height(caretH)) {
                Canvas(
                    modifier = Modifier
                        .offset(x = (caretCenterX - caretW / 2))
                        .size(caretW, caretH)
                ) {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, MENU_BG)
                }
            }

            // Body z pozycjami (wg Figmy 4719:5343: kontener radius 8, padding 8, gap 8;
            // item padding px24/py16, radius 4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(sx(8)))
                    .background(MENU_BG)
                    .padding(sx(8))
                    .then(
                        if (items.size > maxVisible)
                            Modifier.heightIn(max = sy(72) * maxVisible).verticalScroll(scroll)
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sy(8))
            ) {
                items.forEachIndexed { index, item ->
                    val isFocused = index == focusedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(sx(4)))
                            .background(if (isFocused) MENU_FOCUS_PILL else Color.Transparent)
                            .padding(horizontal = sx(24), vertical = sy(16)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.label,
                            color = when {
                                item.destructive -> MENU_TEXT_DESTRUCTIVE
                                else -> MENU_TEXT
                            },
                            fontSize = (24 * sx(1).value / 1).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Chevron ⌄ gdy lista się nie mieści
                if (items.size > maxVisible) {
                    Text(
                        text = "⌄",
                        color = MENU_TEXT,
                        fontSize = (24 * sx(1).value / 1).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Inicjalizacja fokusa + gating (wzorzec PipDialogMenu): czekamy aż KeyUp z otwarcia
        // przeleci, zanim włączymy obsługę klawiszy menu.
        LaunchedEffect(Unit) {
            // Łap fokus NATYCHMIAST (bez clearFocus) — żeby trzymany OK nie przeciekł na
            // chipy Sortuj/Kategoria w luce bez fokusu. Gating ignoruje resztki z otwarcia.
            inputEnabled = false
            menuFocusRequester.requestFocus()
            delay(300)
            inputEnabled = true
        }
        // UWAGA: celowo NIE czyścimy fokusa przy zamknięciu — przywrócenie fokusa na kafel
        // robi grid (onDismiss requestFocus). clearFocus tutaj powodował mignięcie (fokus
        // skakał na przypadkowy element zanim grid ustawił go na właściwej miniaturce).
    }
}
