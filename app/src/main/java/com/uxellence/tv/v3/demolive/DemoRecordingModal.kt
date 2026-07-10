package com.uxellence.tv.v3.demolive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.R
import kotlinx.coroutines.delay

/**
 * MODAL NAGRYWANIA — flow Figma "Nagrywanie serii" (4600:16894).
 *
 * Wygląd: blenda black 40 na całym ekranie, wycentrowany modal 967×384
 * (czarny, radius 24, border white20): tytuł + podtytuł, "Zachowaj na: X",
 * rząd przycisków [Nagraj odcinek][Nagraj serię][Opcje nagrania][Anuluj]
 * (fokus = aqua tło + purpurowy tekst).
 *
 * Klawisze: LEFT/RIGHT fokus, OK wybór (tylko repeatCount==0), BACK zamyka.
 * Input gating jak PipDialogMenu: fokus przejmowany po otwarciu, wszystkie
 * klawisze konsumowane — nic nie przecieka do warstw pod spodem.
 */
private val AQUA = Color(0xFF5AECD3)
private val WHITE = Color(0xFFEEEEEE)
private val WHITE60 = Color(0x99EEEEEE)
private val PURPLE = Color(0xFF48227C)
private val BTN_BG = Color(0xFF2E2E2E)
private val MODAL_BG = Color(0xF20D0D0D)

@Composable
internal fun DemoRecordingModal(
    title: String,
    subtitle: String,
    keepLabel: String,
    onRecordEpisode: () -> Unit,
    onRecordSeries: () -> Unit,
    onDismiss: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val buttons = listOf("Nagraj odcinek", "Nagraj serię", "Opcje nagrania", "Anuluj")
    var focusIndex by remember { mutableIntStateOf(0) }
    var inputEnabled by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    BackHandler(enabled = true) { onDismiss() }
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
        delay(150)
        inputEnabled = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color(0x66000000))   // Blenda Black 40
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (!inputEnabled) return@onKeyEvent true
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> focusIndex = (focusIndex - 1).coerceAtLeast(0)
                        Key.DirectionRight -> focusIndex =
                            (focusIndex + 1).coerceAtMost(buttons.size - 1)
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                when (focusIndex) {
                                    0 -> onRecordEpisode()
                                    1 -> onRecordSeries()
                                    2 -> android.widget.Toast.makeText(
                                        context,
                                        "Opcje nagrania — wkrótce w makiecie",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    3 -> onDismiss()
                                }
                            }
                        }
                    }
                }
                true   // konsumuj WSZYSTKO — modal jest jedynym odbiorcą klawiszy
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(sx(967))
                .background(MODAL_BG, RoundedCornerShape(sx(24)))
                .border(1.5.dp, Color(0x33EEEEEE), RoundedCornerShape(sx(24)))
                .padding(horizontal = sx(48), vertical = sy(48))
        ) {
            Text(
                text = title,
                color = WHITE,
                fontSize = demoSp(32, sy),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(sy(16)))
            Text(
                text = subtitle,
                color = WHITE60,
                fontSize = demoSp(24, sy),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(sy(40)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Zachowaj na:  ",
                    color = WHITE60,
                    fontSize = demoSp(26, sy)
                )
                Text(
                    text = keepLabel,
                    color = WHITE,
                    fontSize = demoSp(26, sy),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(sy(48)))
            Row {
                buttons.forEachIndexed { i, label ->
                    if (i > 0) Spacer(modifier = Modifier.width(sx(24)))
                    val focused = i == focusIndex
                    Box(
                        modifier = Modifier
                            .height(sy(72))
                            .background(
                                if (focused) AQUA else BTN_BG,
                                RoundedCornerShape(sx(8))
                            )
                            .padding(horizontal = sx(32)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (focused) PURPLE else WHITE,
                            fontSize = demoSp(24, sy),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * TOAST NAGRYWANIA — potwierdzenie zlecenia (Figma 6169:96935 / 6169:97229).
 * "Rozpoczęto nagrywanie odcinka:" / "Zlecono nagrywanie serii:" + tytuł
 * + wskazówka o sekcji Nagrania. Auto-hide po [autoHideMs].
 */
@Composable
internal fun DemoRecordingToast(
    header: String,
    title: String,
    visible: Boolean,
    onHidden: () -> Unit,
    autoHideMs: Long = 5_000L,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    if (!visible) return
    LaunchedEffect(header, title) {
        delay(autoHideMs)
        onHidden()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(29f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(sx(769))
                .background(MODAL_BG, RoundedCornerShape(sx(24)))
                .border(1.5.dp, Color(0x33EEEEEE), RoundedCornerShape(sx(24)))
                .padding(horizontal = sx(48), vertical = sy(32))
        ) {
            // Ikona nagrywania w kółku (aqua)
            Box(
                modifier = Modifier
                    .size(sx(64), sy(64))
                    .border(2.dp, AQUA, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.demo_ic_rec),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(AQUA),
                    modifier = Modifier.size(sx(36), sy(36))
                )
            }
            Spacer(modifier = Modifier.width(sx(40)))
            Column {
                Text(
                    text = header,
                    color = WHITE60,
                    fontSize = demoSp(20, sy),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(sy(8)))
                Text(
                    text = title,
                    color = WHITE,
                    fontSize = demoSp(32, sy),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(sy(16)))
                Text(
                    text = "Nagranie znajdziesz w zakładce Moje w menu górnym.",
                    color = WHITE60,
                    fontSize = demoSp(20, sy),
                    lineHeight = demoSp(28, sy)
                )
            }
        }
    }
}
