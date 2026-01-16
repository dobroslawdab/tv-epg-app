package com.uxellence.tv.v3.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.VodSlideData
import kotlinx.coroutines.delay

/**
 * PurchaseScreen - Strona zakupu/wypożyczenia filmu
 *
 * Design z Figma: Node 844-13950
 * Tło: #281443 (gradient-purple-dark)
 *
 * Layout:
 * - "Wypożyczasz" (32px, Bold, letterSpacing 0.64)
 * - Tytuł filmu (64px, Medium, lineHeight 88)
 * - Cena "X zł / 48 h" (32px, Bold, letterSpacing 0.64)
 * - Info o płatności (32px, Bold, lineHeight 40)
 * - Regulamin (28px, Medium, lineHeight 40)
 * - Email info (28px, Medium + 32px Bold dla adresu)
 * - Przyciski: "Wypożyczam i płacę", "Zmień e-mail", "Regulamin"
 *
 * @param item Dane filmu z VodSlideData
 * @param userEmail Email użytkownika
 * @param userPhoneNumber Numer telefonu użytkownika (do wyświetlenia w info o płatności)
 * @param onBackPressed Callback dla BACK
 * @param onConfirmPurchase Callback dla "Wypożyczam i płacę"
 * @param onChangeEmail Callback dla "Zmień e-mail"
 * @param onShowRegulations Callback dla "Regulamin"
 */
@Composable
fun PurchaseScreen(
    item: VodSlideData,
    userEmail: String = "adres@domena.pl",
    userPhoneNumber: String = "690100003",
    onBackPressed: () -> Unit,
    onConfirmPurchase: () -> Unit = {},
    onChangeEmail: () -> Unit = {},
    onShowRegulations: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int): Dp = (px * scaleX).dp
    fun sy(px: Int): Dp = (px * scaleY).dp

    // Focus management for buttons
    var focusedButtonIndex by remember { mutableIntStateOf(0) }
    val buttonFocusRequesters = remember { List(3) { FocusRequester() } }

    // Request focus on first button when screen loads
    LaunchedEffect(Unit) {
        delay(100)
        buttonFocusRequesters.getOrNull(0)?.requestFocus()
    }

    // Button labels
    val buttons = listOf(
        "Wypożyczam i płacę",
        "Zmień e-mail",
        "Regulamin"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF281443)) // Dark purple gradient background
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBackPressed()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusedButtonIndex > 0) {
                            focusedButtonIndex--
                            buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (focusedButtonIndex < buttons.size - 1) {
                            focusedButtonIndex++
                            buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when (focusedButtonIndex) {
                            0 -> onConfirmPurchase()
                            1 -> onChangeEmail()
                            2 -> onShowRegulations()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Content area - centered vertically, left-aligned
        // Position: left 10% + 42px = ~234px, vertically centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(234))
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(sy(40))
        ) {
            // Title section
            Column(
                verticalArrangement = Arrangement.spacedBy(sy(16))
            ) {
                // "Wypożyczasz" label
                Text(
                    text = "Wypożyczasz",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(32).value.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.64.sp,
                    lineHeight = sy(48).value.sp
                )

                // Movie title (64px, Medium)
                Text(
                    text = item.title,
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(64).value.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = sy(88).value.sp,
                    modifier = Modifier.widthIn(max = sx(1077))
                )

                // Price
                Text(
                    text = item.price,
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(32).value.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.64.sp,
                    lineHeight = sy(48).value.sp
                )
            }

            // Content section
            Column(
                modifier = Modifier.width(sx(981)),
                verticalArrangement = Arrangement.spacedBy(sy(32))
            ) {
                // Payment info (32px, Bold)
                Text(
                    text = "Kwotę doliczymy do Twojego rachunku w Play\ndla numeru $userPhoneNumber. Jeśli korzystasz z oferty\nna kartę lub mix, pobierzemy środki z Twojego konta.",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(32).value.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = sy(40).value.sp
                )

                // Legal disclaimer (28px, Medium)
                Text(
                    text = "Klikając Wypożyczam i płacę wyrażasz zgodę na rozpoczęcie dostarczania treści cyfrowych przed upływem terminu odstąpienia od umowy (kiedy uruchomimy usługę, stracisz prawo do odstąpienia od umowy).",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(28).value.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = sy(40).value.sp
                )

                // Email info
                Column {
                    Text(
                        text = "Adres email, na który wyślemy regulamin:",
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = sy(40).value.sp
                    )
                    Text(
                        text = userEmail,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(32).value.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = sy(40).value.sp
                    )
                }
            }

            // Extra spacing before buttons (100px total: 40px from spacedBy + 60px spacer)
            Spacer(modifier = Modifier.height(sy(60)))

            // Button section
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(24))
            ) {
                buttons.forEachIndexed { index, label ->
                    PurchaseButton(
                        label = label,
                        isFocused = focusedButtonIndex == index,
                        focusRequester = buttonFocusRequesters[index],
                        sx = ::sx,
                        sy = ::sy,
                        onFocusChanged = { isFocused ->
                            if (isFocused) focusedButtonIndex = index
                        },
                        onClick = {
                            when (index) {
                                0 -> onConfirmPurchase()
                                1 -> onChangeEmail()
                                2 -> onShowRegulations()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Purchase button - same style as Figma design
 * Height: 72px, padding: 32px horizontal, radius: 8px
 * Focused: #5FEDD4 bg, #48227C text
 * Unfocused: rgba(238,238,238,0.2) bg, #EEEEEE text
 */
@Composable
private fun PurchaseButton(
    label: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF5FEDD4) else Color(0x33EEEEEE),
        animationSpec = tween(150),
        label = "buttonBgColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
        animationSpec = tween(150),
        label = "buttonTextColor"
    )

    Box(
        modifier = Modifier
            .height(sy(72))
            .clip(RoundedCornerShape(sx(8)))
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> onFocusChanged(state.isFocused) }
            .focusable()
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = sy(24).value.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.48).sp
        )
    }
}
