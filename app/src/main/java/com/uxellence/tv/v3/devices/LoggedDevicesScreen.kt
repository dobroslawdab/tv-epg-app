package com.uxellence.tv.v3.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Zalogowane urządzenia — PEŁNOEKRANOWA podstrona Konta (pozycja między
 * "Wyszukiwaniem kanałów TV" a "Pomocą"), renderowana z MainActivity jako
 * NavigationScreen.DEVICES — bez top menu, jak PackageDetailScreen.
 *
 * Wymagania makiety:
 *  - lista urządzeń zalogowanych na koncie (dekoder, telefon, komputer, tablet, TV)
 *  - z BIEŻĄCEGO dekodera nie da się wylogować (brak akcji + wyjaśnienie po OK)
 *  - pozostałe urządzenia można wylogować zdalnie (z potwierdzeniem)
 *  - limit [DeviceSessionManager.DEVICE_LIMIT] urządzeń — licznik i komunikat o limicie
 *
 * KEY HANDLER: LoggedDevices
 * Scope: UP/DOWN po liście, OK = potwierdzenie wylogowania (lub info dla dekodera),
 *        w modalu LEWO/PRAWO przełącza przyciski, OK zatwierdza,
 *        BACK = zamknięcie modalu, a przy zamkniętym modalu wyjście z ekranu.
 *
 * BACK idzie przez [BackHandler] (dispatcher), a nie przez onPreviewKeyEvent —
 * KeyDown przełącza widok, a zabłąkany KeyUp trafiałby w okno bez fokusu i
 * domyślna obsługa zamykałaby makietę (dwufazowy bug BACK, patrz CLAUDE.md).
 * Dlatego: Back na KeyDown zwraca false, a niekonsumowane KeyUp poza Back = true.
 *
 * Fokus modalu jest STANOWY (bez własnych focusable) — modal znika z kompozycji
 * razem ze swoim focusable i Compose zostawałby bez właściciela fokusu
 * (lekcja #11 "Focus the container, not the item").
 */
@Composable
fun LoggedDevicesScreen(
    onClose: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val colorBg = Color(0xFF281443)
    val colorTile = Color(0xFF1D1030)
    val colorFocus = Color(0xFF5AECD3)
    val colorCard = Color(0x33000000)
    val colorPrimary = Color(0xFF5FEDD4)
    val colorOnPrimary = Color(0xFF48227C)
    val colorText = Color(0xFFEEEEEE)
    val colorTextDim = Color(0xCCEEEEEE)
    val colorWarn = Color(0xFFFFC46B)

    val devices by DeviceSessionManager.devices

    var focusedIndex by remember { mutableStateOf(0) }
    // null = brak modalu; wpp. urządzenie, którego dotyczy modal
    var dialogDevice by remember { mutableStateOf<DeviceSessionManager.LoggedDevice?>(null) }
    // 0 = "Wyloguj" (primary), 1 = "Anuluj". Dla dekodera tylko jeden przycisk.
    var dialogButtonIndex by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val cardFocusRequesters = remember(devices.size) {
        (devices.indices).associateWith { FocusRequester() }
    }

    // Po wylogowaniu lista się skraca — utrzymaj indeks w zakresie
    LaunchedEffect(devices.size) {
        if (focusedIndex > devices.lastIndex) focusedIndex = devices.lastIndex.coerceAtLeast(0)
    }

    // Fokus realny idzie na kartę; przy otwartym modalu nie ruszamy fokusu,
    // żeby po jego zamknięciu właściciel fokusu wciąż istniał
    LaunchedEffect(focusedIndex, devices.size, dialogDevice == null) {
        if (dialogDevice == null) {
            try { cardFocusRequesters[focusedIndex]?.requestFocus() } catch (_: Exception) {}
            if (devices.isNotEmpty()) {
                try { listState.animateScrollToItem(focusedIndex) } catch (_: Exception) {}
            }
        }
    }

    BackHandler(enabled = true) {
        if (dialogDevice != null) dialogDevice = null else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    // Konsumuj zabłąkane KeyUp, ale NIE Back (obsługa w BackHandler)
                    return@onPreviewKeyEvent event.key != Key.Back
                }
                val dialog = dialogDevice
                if (dialog != null) {
                    when (event.key) {
                        Key.Back -> false
                        Key.DirectionLeft -> {
                            if (!dialog.isCurrent) dialogButtonIndex = 0
                            true
                        }
                        Key.DirectionRight -> {
                            if (!dialog.isCurrent) dialogButtonIndex = 1
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (dialog.isCurrent) {
                                dialogDevice = null              // modal informacyjny: OK = zamknij
                            } else if (dialogButtonIndex == 0) {
                                DeviceSessionManager.logout(dialog.id)
                                dialogDevice = null
                            } else {
                                dialogDevice = null              // Anuluj
                            }
                            true
                        }
                        // Modal blokuje resztę nawigacji
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.Back -> false
                        Key.DirectionUp -> {
                            if (focusedIndex > 0) focusedIndex-- else onClose()
                            true
                        }
                        Key.DirectionDown -> {
                            if (focusedIndex < devices.lastIndex) focusedIndex++
                            true
                        }
                        Key.DirectionLeft -> {
                            onClose()
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            devices.getOrNull(focusedIndex)?.let { device ->
                                dialogButtonIndex = 0
                                dialogDevice = device
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Lewy margines jak w detalu pakietu / Wypożycz
                .padding(start = sx(234), top = sy(96), end = sx(80))
        ) {
            Text(
                text = "Zalogowane urządzenia",
                color = colorText,
                fontSize = sy(48).value.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(sy(12)))

            Text(
                text = "Wykorzystano ${DeviceSessionManager.usedSlots()} z " +
                    "${DeviceSessionManager.DEVICE_LIMIT} urządzeń na koncie",
                color = colorTextDim,
                fontSize = sy(28).value.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(sy(8)))

            Text(
                text = if (DeviceSessionManager.isLimitReached()) {
                    "Osiągnięto limit. Wyloguj urządzenie, aby zalogować nowe."
                } else {
                    "Możesz zalogować jeszcze ${DeviceSessionManager.freeSlots()} " +
                        slotWord(DeviceSessionManager.freeSlots()) + "."
                },
                color = if (DeviceSessionManager.isLimitReached()) colorWarn else colorTextDim,
                fontSize = sy(24).value.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(sy(40)))

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(sy(20)),
                modifier = Modifier.widthIn(max = sx(1000))
            ) {
                itemsIndexed(devices, key = { _, device -> device.id }) { index, device ->
                    DeviceCard(
                        device = device,
                        isFocused = dialogDevice == null && focusedIndex == index,
                        focusRequester = cardFocusRequesters[index],
                        colorCard = colorCard,
                        colorFocus = colorFocus,
                        colorText = colorText,
                        colorTextDim = colorTextDim,
                        sx = sx,
                        sy = sy
                    )
                }
            }
        }

        dialogDevice?.let { device ->
            DeviceDialog(
                device = device,
                selectedButton = dialogButtonIndex,
                colorTile = colorTile,
                colorPrimary = colorPrimary,
                colorOnPrimary = colorOnPrimary,
                colorText = colorText,
                colorTextDim = colorTextDim,
                sx = sx,
                sy = sy
            )
        }
    }
}

/** Odmiana rzeczownika po liczbie wolnych miejsc (1 urządzenie / 2-4 urządzenia). */
private fun slotWord(count: Int): String = when (count) {
    1 -> "urządzenie"
    else -> "urządzenia"
}

private fun iconFor(kind: DeviceSessionManager.DeviceKind): ImageVector = when (kind) {
    DeviceSessionManager.DeviceKind.BOX -> Icons.Default.Router
    DeviceSessionManager.DeviceKind.PHONE -> Icons.Default.Smartphone
    DeviceSessionManager.DeviceKind.COMPUTER -> Icons.Default.Computer
    DeviceSessionManager.DeviceKind.TABLET -> Icons.Default.TabletAndroid
    DeviceSessionManager.DeviceKind.TV -> Icons.Default.Tv
}

/**
 * Karta urządzenia — ten sam język wizualny co AccountMenuItemCard
 * (bg rgba(0,0,0,0.2), fokus 8px #5AECD3, radius 8px, ikona 64px).
 */
@Composable
private fun DeviceCard(
    device: DeviceSessionManager.LoggedDevice,
    isFocused: Boolean,
    focusRequester: FocusRequester?,
    colorCard: Color,
    colorFocus: Color,
    colorText: Color,
    colorTextDim: Color,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .width(sx(1000))
            .height(sy(136))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .then(
                if (isFocused) Modifier.border(
                    width = sx(8),
                    color = colorFocus,
                    shape = RoundedCornerShape(sx(8))
                ) else Modifier
            )
            .clip(RoundedCornerShape(sx(8)))
            .background(colorCard)
            .padding(horizontal = sx(32), vertical = sy(24))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(24))
        ) {
            Icon(
                imageVector = iconFor(device.kind),
                contentDescription = device.kindLabel,
                modifier = Modifier.size(sx(64), sy(64)),
                tint = colorText
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(sy(4))
            ) {
                Text(
                    text = device.name,
                    color = colorText,
                    fontSize = sy(32).value.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.kindLabel} · ${device.lastActive}",
                    color = colorTextDim,
                    fontSize = sy(24).value.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Akcja po prawej: dekoder nie ma wylogowania — kłódka zamiast akcji
            if (device.isCurrent) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Nie można wylogować tego urządzenia",
                    modifier = Modifier.size(sx(40), sy(40)),
                    tint = colorTextDim
                )
            } else {
                Text(
                    text = "Wyloguj",
                    color = if (isFocused) colorFocus else colorText,
                    fontSize = sy(26).value.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Modal: potwierdzenie wylogowania (urządzenia zdalne) albo wyjaśnienie,
 * dlaczego bieżącego dekodera wylogować nie można.
 */
@Composable
private fun DeviceDialog(
    device: DeviceSessionManager.LoggedDevice,
    selectedButton: Int,
    colorTile: Color,
    colorPrimary: Color,
    colorOnPrimary: Color,
    colorText: Color,
    colorTextDim: Color,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(sx(960))
                .clip(RoundedCornerShape(sx(16)))
                .background(colorTile)
                .padding(horizontal = sx(56), vertical = sy(48))
        ) {
            Text(
                text = if (device.isCurrent) {
                    "Nie możesz wylogować tego urządzenia"
                } else {
                    "Wylogować urządzenie?"
                },
                color = colorText,
                fontSize = sy(36).value.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(sy(20)))

            Text(
                text = if (device.isCurrent) {
                    "Korzystasz właśnie z tego dekodera, więc nie da się z niego " +
                        "wylogować zdalnie. Zrób to z innego urządzenia albo " +
                        "skontaktuj się z obsługą."
                } else {
                    "${device.name} straci dostęp do Telewizji Play. Ponowne " +
                        "zalogowanie będzie wymagać danych Twojego konta."
                },
                color = colorTextDim,
                fontSize = sy(28).value.sp,
                lineHeight = sy(38).value.sp
            )

            Spacer(modifier = Modifier.height(sy(40)))

            Row(horizontalArrangement = Arrangement.spacedBy(sx(24))) {
                if (device.isCurrent) {
                    DialogButton(
                        label = "OK",
                        isSelected = true,
                        colorPrimary = colorPrimary,
                        colorOnPrimary = colorOnPrimary,
                        colorText = colorText,
                        sx = sx,
                        sy = sy
                    )
                } else {
                    DialogButton(
                        label = "Wyloguj",
                        isSelected = selectedButton == 0,
                        colorPrimary = colorPrimary,
                        colorOnPrimary = colorOnPrimary,
                        colorText = colorText,
                        sx = sx,
                        sy = sy
                    )
                    DialogButton(
                        label = "Anuluj",
                        isSelected = selectedButton == 1,
                        colorPrimary = colorPrimary,
                        colorOnPrimary = colorOnPrimary,
                        colorText = colorText,
                        sx = sx,
                        sy = sy
                    )
                }
            }
        }
    }
}

/** Przycisk modalu — kolory 1:1 jak ActionButton w MovieDetail / detalu pakietu. */
@Composable
private fun DialogButton(
    label: String,
    isSelected: Boolean,
    colorPrimary: Color,
    colorOnPrimary: Color,
    colorText: Color,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) colorPrimary else colorText.copy(alpha = 0.2f),
                shape = RoundedCornerShape(sx(12))
            )
            .padding(horizontal = sx(40), vertical = sy(20))
    ) {
        Text(
            text = label,
            color = if (isSelected) colorOnPrimary else colorText,
            fontSize = sy(28).value.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
