package com.example.tv

// Model danych dla głównego menu nawigacyjnego

data class MenuItem(
    val id: String,
    val title: String,
    val screen: MenuScreen
)

enum class MenuScreen {
    SEARCH,
    MOJE,
    START,
    TELEWIZJA,
    KINO_PLAY,
    WIDEO,
    APLIKACJE
}

enum class MenuItemState {
    NORMAL,    // rgba(238,238,238,0.05), tekst #EEEEEE
    SELECTED,  // białe tło, tekst ciemny
    FOCUSED    // #5AECD3, tekst #48227C
}

enum class FocusArea {
    LEFT_MENU,     // Lewe menu (search, moje, start, etc.)
    RIGHT_MENU,    // Prawe menu (notifications, settings)
    CONTENT        // Obszar contentu
}

enum class RightMenuItem {
    NOTIFICATIONS,
    SETTINGS
}

data class TopMenuState(
    val focusedItemId: String = "START",
    val selectedItemId: String = "START",
    val isMenuFocused: Boolean = true, // czy fokus jest na menu czy na contencie
    val focusedArea: FocusArea = FocusArea.LEFT_MENU, // który obszar ma fokus
    val rightMenuFocusedItem: RightMenuItem? = null // który element prawego menu ma fokus
)