package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.utils.VersionTracker

/**
 * WhatsNewScreen
 *
 * Ekran pokazywany po aktualizacji aplikacji do nowej wersji.
 * Wyświetla listę zmian i ulepszeń.
 *
 * Pokazuje się tylko raz po aktualizacji, następnie jest pomijany.
 *
 * Design based on Figma: TV-18 (node-id=6287-118360)
 * - Full screen: 1920x1080px, purple background #48227C
 * - Main frame: 1499×842px at (211, 119), black #000000, radius 33px, shadow 40px
 * - Left column: 532px width
 * - Right side: 817×789px with radial gradient
 */
@Composable
fun WhatsNewScreen(
    onContinue: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val buttonFocusRequester = remember { FocusRequester() }

    // Auto-fokus na przycisku OK po załadowaniu ekranu
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        buttonFocusRequester.requestFocus()
    }

    // Full screen purple background (#48227C)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                // Pozwól na pominięcie ekranu przez BACK
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    VersionTracker.markWhatsNewShown(context)
                    onContinue()
                    true
                } else false
            }
    ) {
        // Main frame: 1499×842px at position (211, 119)
        Box(
            modifier = Modifier
                .offset(x = sx(211), y = sy(119))
                .size(width = sx(1499), height = sy(842))
                .shadow(
                    elevation = sx(40),
                    shape = RoundedCornerShape(sx(33))
                )
                .clip(RoundedCornerShape(sx(33)))
                .background(Color(0xFF000000))
                .padding(vertical = sy(26))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT COLUMN (532px width)
                Column(
                    modifier = Modifier
                        .width(sx(532))
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Version text - Inter Bold 40px, #9EFFEE (cyan/mint)
                    Text(
                        text = "Wersja ${BuildConfig.VERSION_NAME}",
                        style = TextStyle(
                            fontSize = (40 * sy(1).value / 1).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9EFFEE)
                        )
                    )

                    Spacer(modifier = Modifier.height(sy(68)))

                    // Description - Inter Regular 20px, #9EFFEE
                    Text(
                        text = """Ostatnie zmiany:
w zakładce Telewizja  po zatrzymaniu się na pierwszym slajdzie w sliderze włączy się player
kliknięcie OK przenosi do playera full screen""",
                        style = TextStyle(
                            fontSize = (20 * sy(1).value / 1).sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF9EFFEE),
                            lineHeight = (20 * 1.5 * sy(1).value / 1).sp
                        )
                    )

                    Spacer(modifier = Modifier.height(sy(80)))

                    // OK Button - 80px height, white bg, purple text, pill shape
                    Button(
                        onClick = {
                            VersionTracker.markWhatsNewShown(context)
                            onContinue()
                        },
                        modifier = Modifier
                            .focusRequester(buttonFocusRequester)
                            .height(sy(80))
                            .widthIn(min = sx(120)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF48227C)
                        ),
                        shape = RoundedCornerShape(sx(64)) // Pill shape
                    ) {
                        Text(
                            text = "OK",
                            style = TextStyle(
                                fontSize = (24 * sy(1).value / 1).sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(sx(50)))

                // RIGHT SIDE - 817×789px with radial gradient
                Box(
                    modifier = Modifier
                        .size(width = sx(817), height = sy(789))
                        .clip(RoundedCornerShape(sx(33)))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF9EFFEE).copy(alpha = 0.3f),
                                    Color(0xFF48227C).copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    // Placeholder for image/illustration
                    // Can be added later when design asset is available
                }
            }
        }
    }
}
