package com.example.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tv.ui.theme.figmaRadialBackground
import com.example.tv.components.SliderComponent
import com.example.tv.components.ShortcutComponent
import com.example.tv.components.ReusableTopMenu
import kotlinx.coroutines.launch

// Data model for component showcase
data class ComponentInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val content: @Composable () -> Unit
)

/**
 * Component Showcase Screen
 * Displays all available components in the project for testing and demonstration
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComponentShowcaseScreen() {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Define all available reusable components
    val components = remember {
        listOf(
            ComponentInfo(
                id = "slider_component",
                name = "SliderComponent",
                description = "Reusable horizontal movie/content slider with focus management and automatic scrolling",
                category = "Navigation"
            ) {
                SliderComponent(
                    modifier = Modifier.fillMaxWidth(),
                    isSectionFocused = true
                )
            },
            ComponentInfo(
                id = "shortcut_component",
                name = "ShortcutComponent", 
                description = "Reusable horizontal shortcuts row with Lottie animations and focus states",
                category = "Navigation"
            ) {
                ShortcutComponent(
                    modifier = Modifier.fillMaxWidth(),
                    isSectionFocused = false
                )
            },
            ComponentInfo(
                id = "reusable_topmenu",
                name = "ReusableTopMenu",
                description = "Complete reusable top menu with tabs, right menu, digital clock, and content management",
                category = "Layout"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(400)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ReusableTopMenu Demo\n(Complete menu system with clock and navigation)\n\nUsed in: Version002Screen, TopMenuScreen",
                        color = Color.White,
                        fontSize = sy(14).value.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        lineHeight = sy(18).value.sp
                    )
                }
            }
        )
    }

    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(components.size) { List(components.size) { FocusRequester() } }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < components.size) {
            scope.launch { 
                listState.animateScrollToItem(focusedIndex)
                focusRequesters[focusedIndex].requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val newIndex = when (event.key) {
                    Key.DirectionUp -> if (focusedIndex > 0) focusedIndex - 1 else focusedIndex
                    Key.DirectionDown -> if (focusedIndex < components.size - 1) focusedIndex + 1 else focusedIndex
                    else -> focusedIndex
                }

                if (newIndex != focusedIndex) {
                    focusedIndex = newIndex
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LaunchedEffect(Unit) {
            if (components.isNotEmpty()) {
                focusRequesters[0].requestFocus()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(sx(40))
        ) {
            // Header
            Text(
                text = "Reusable Components",
                fontSize = sy(32).value.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = sy(20))
            )

            Text(
                text = "Komponenty wielokrotnego użytku - gotowe do wykorzystania w różnych ekranach",
                fontSize = sy(16).value.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = sy(30))
            )

            // Components list
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(sy(40)),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(components) { index, component ->
                    ComponentShowcaseItem(
                        component = component,
                        isFocused = index == focusedIndex,
                        focusRequester = focusRequesters[index],
                        onFocusChanged = { isFocused ->
                            if (isFocused) focusedIndex = index
                        },
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentShowcaseItem(
    component: ComponentInfo,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isItemFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isItemFocused) {
                    isItemFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            },
        shape = RoundedCornerShape(sx(12)),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0x1A5AECD3) else Color(0x0A000000)
        ),
        border = if (isFocused) 
            androidx.compose.foundation.BorderStroke(sx(2), Color(0xFF5AECD3))
        else 
            androidx.compose.foundation.BorderStroke(sx(1), Color(0x20FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sx(20))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = component.name,
                        fontSize = sy(18).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) Color(0xFF5AECD3) else Color.White
                    )
                    Text(
                        text = component.category,
                        fontSize = sy(12).value.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(top = sy(2))
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isFocused) Color(0xFF5AECD3) else Color(0xFF444444),
                            shape = RoundedCornerShape(sx(16))
                        )
                        .padding(horizontal = sx(12), vertical = sy(4))
                ) {
                    Text(
                        text = component.id,
                        fontSize = sy(10).value.sp,
                        color = if (isFocused) Color(0xFF48227C) else Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Description
            Text(
                text = component.description,
                fontSize = sy(14).value.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = sy(12))
            )

            // Component preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(sx(8))
                    )
                    .padding(sx(16))
            ) {
                component.content()
            }
        }
    }
}