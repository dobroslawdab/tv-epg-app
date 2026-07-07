package com.uxellence.tv.v3.demolive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * PASEK POWIADOMIEŃ dema — wg dostarczonego layoutu:
 * RelativeLayout 995×104dp, wycentrowany poziomo, marginTop 841;
 * tło czarne 90% (#E6000000) z ramką #99EEEEEE (stroke 8 w wektorze
 * 1003×112 → ~4px na stronę). Tekst wycentrowany, auto-hide.
 */
@Composable
internal fun DemoInfoToast(
    text: String?,
    onHidden: () -> Unit,
    autoHideMs: Long = 3_500L,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    if (text == null) return
    LaunchedEffect(text) {
        delay(autoHideMs)
        onHidden()
    }
    Box(modifier = Modifier.fillMaxSize().zIndex(40f)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = sy(841))
                .size(sx(995), sy(104))
                .background(Color(0xE6000000))
                .border(sx(4), Color(0x99EEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFFEEEEEE),
                fontSize = demoSp(28, sy),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = sx(40))
            )
        }
    }
}
