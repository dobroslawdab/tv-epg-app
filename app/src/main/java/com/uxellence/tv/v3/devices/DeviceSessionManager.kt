package com.uxellence.tv.v3.devices

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Zalogowane urządzenia konta — makieta zarządzania sesjami.
 *
 * Stan trzymany W PAMIĘCI (Compose-observable [MutableState]), świadomie BEZ
 * SharedPreferences: makieta ma wracać do pełnych 5/5 przy każdym starcie apki,
 * bo inaczej po jednym pokazie "wyloguj" demo zostaje trwale okrojone i limitu
 * nie ma już czym zademonstrować. Reset ręczny: [reset].
 *
 * Startowo lista jest PEŁNA (5 z 5) — to jedyny stan, w którym widać komunikat
 * o limicie; po pierwszym wylogowaniu makieta pokazuje stan "wolne miejsce".
 */
object DeviceSessionManager {

    /** Limit urządzeń na koncie (wymóg produktowy). */
    const val DEVICE_LIMIT = 5

    enum class DeviceKind { BOX, PHONE, COMPUTER, TABLET, TV }

    data class LoggedDevice(
        val id: String,
        val name: String,
        /** Typ + aplikacja, np. "Telefon · aplikacja Play Now". */
        val kindLabel: String,
        /** Ostatnia aktywność, np. "Aktywne dziś, 20:14". */
        val lastActive: String,
        val kind: DeviceKind,
        /** Dekoder, na którym działa ta apka — z niego NIE da się wylogować. */
        val isCurrent: Boolean = false
    )

    val devices: MutableState<List<LoggedDevice>> = mutableStateOf(defaults())

    /**
     * Sygnał "wróć fokusem na pozycję Zalogowane urządzenia" po zamknięciu
     * pełnoekranowego LoggedDevicesScreen — ustawiany w MainActivity.onClose,
     * konsumowany (i zerowany) przez auto-focus w AccountChannelsScreen.
     */
    var pendingAccountRefocus: Boolean = false

    /** Liczba zajętych miejsc z limitu. */
    fun usedSlots(): Int = devices.value.size

    fun freeSlots(): Int = (DEVICE_LIMIT - usedSlots()).coerceAtLeast(0)

    fun isLimitReached(): Boolean = usedSlots() >= DEVICE_LIMIT

    /**
     * Zdalne wylogowanie urządzenia. Bieżący dekoder jest ignorowany — blokada
     * jest tu, a nie tylko w UI, żeby żaden przyszły wywołujący jej nie ominął.
     */
    fun logout(deviceId: String) {
        val target = devices.value.firstOrNull { it.id == deviceId } ?: return
        if (target.isCurrent) return
        devices.value = devices.value.filterNot { it.id == deviceId }
    }

    /** Przywróć pełną listę 5/5 (debug / kolejny pokaz bez restartu apki). */
    fun reset() {
        devices.value = defaults()
    }

    private fun defaults(): List<LoggedDevice> = listOf(
        LoggedDevice(
            id = "box_this",
            name = "PLAY BOX 4K",
            kindLabel = "Dekoder · to urządzenie",
            lastActive = "Używane teraz",
            kind = DeviceKind.BOX,
            isCurrent = true
        ),
        LoggedDevice(
            id = "phone_s24",
            name = "Samsung Galaxy S24",
            kindLabel = "Telefon · aplikacja Play Now",
            lastActive = "Aktywne dziś, 20:14",
            kind = DeviceKind.PHONE
        ),
        LoggedDevice(
            id = "computer_mbp",
            name = "MacBook Pro",
            kindLabel = "Komputer · przeglądarka Chrome",
            lastActive = "Aktywne wczoraj, 22:03",
            kind = DeviceKind.COMPUTER
        ),
        LoggedDevice(
            id = "tablet_ipad",
            name = "iPad Air",
            kindLabel = "Tablet · aplikacja Play Now",
            lastActive = "Aktywne 3 dni temu",
            kind = DeviceKind.TABLET
        ),
        LoggedDevice(
            id = "tv_samsung",
            name = "Samsung Smart TV",
            kindLabel = "Telewizor · aplikacja Play Now",
            lastActive = "Aktywne 12.08.2026",
            kind = DeviceKind.TV
        )
    )
}
