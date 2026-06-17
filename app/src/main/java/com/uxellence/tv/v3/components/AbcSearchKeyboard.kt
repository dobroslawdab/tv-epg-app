package com.uxellence.tv.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.R

/** Reusable ABC keyboard pulled out of search/SearchScreen.kt so it can be embedded in
 *  KinoGridScreen (and any other surface that needs an inline soft keyboard). The component
 *  is purely visual — focus and number-mode state are owned by the caller, which decides
 *  what to do on key press.
 *
 *  Layout matches SearchScreen:
 *    rows 0..3 — letters (6 per row)
 *    row 4     — y, z (2 keys, left aligned)
 *    row 5     — ABC/123 toggle, SPACE (4× width), backspace
 */

val KB_ABC: List<List<String>> = listOf(
    listOf("a", "b", "c", "d", "e", "f"),
    listOf("g", "h", "i", "j", "k", "l"),
    listOf("m", "n", "o", "p", "q", "r"),
    listOf("s", "t", "u", "v", "w", "x"),
    listOf("y", "z"),
    listOf("ABC", "SPACE", "⌫")
)

val KB_123: List<List<String>> = listOf(
    listOf("1", "2", "3", "4", "5", "6"),
    listOf("7", "8", "9", "0", "@", "#"),
    listOf("\$", "%", "&", "*", "(", ")"),
    listOf("-", "_", "+", "=", "!", "?"),
    listOf(".", ",", ":", ";", "/", "'"),
    listOf("ABC", "SPACE", "⌫")
)

const val KEY_BACKSPACE = "⌫"
const val KEY_SPACE = "SPACE"
const val KEY_ABC_TOGGLE = "ABC"

private val ManropeFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val COLOR_BG = Color(0xFF281443)
private val COLOR_TEXT_PRIMARY = Color(0xFFEEEEEE)
private val COLOR_FOCUS_BORDER = Color(0xFF5FEDD4)
private val COLOR_KEY_BG = Color(0x33EEEEEE)

/** Keyboard rows for the current mode. Caller picks ABC vs 123. */
fun keyboardRows(isNumberMode: Boolean): List<List<String>> =
    if (isNumberMode) KB_123 else KB_ABC

@Composable
fun AbcSearchKeyboard(
    focusedRow: Int,
    focusedCol: Int,
    isNumberMode: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    // True only while the keyboard owns the focus in its parent screen.
    // When false (caller moved focus to the grid / chips), no key renders
    // the focused/aqua style — otherwise the screen would show two focus
    // indicators at once (last key + grid item).
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rows = keyboardRows(isNumberMode)
    val letterW = sx(64)
    val gapW = sx(4)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(sy(4))) {
        rows.forEachIndexed { rowIdx, rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(sx(4))) {
                rowKeys.forEachIndexed { colIdx, key ->
                    val isFocused = isActive && rowIdx == focusedRow && colIdx == focusedCol
                    val keyWidth = when (key) {
                        KEY_SPACE -> letterW * 4 + gapW * 3
                        else -> letterW
                    }
                    val keyBg = if (isFocused) COLOR_FOCUS_BORDER else COLOR_KEY_BG
                    val keyText = if (isFocused) COLOR_BG else COLOR_TEXT_PRIMARY
                    val keyShape = RoundedCornerShape(sx(8))

                    Box(
                        modifier = Modifier
                            .width(keyWidth)
                            .height(sy(64))
                            .background(keyBg, keyShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (key) {
                                KEY_SPACE -> "SPACE"
                                KEY_BACKSPACE -> "⌫"
                                KEY_ABC_TOGGLE -> if (isNumberMode) "abc" else "123"
                                else -> key
                            },
                            color = keyText,
                            fontSize = (if (key == KEY_SPACE) 18 else 24).let { (it * sx(1).value).sp },
                            fontFamily = ManropeFamily,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
