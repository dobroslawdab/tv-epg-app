package com.uxellence.tv.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Filter chip row component for ChannelGridScreen
 * Based on Figma design: node-id=6418:12474
 *
 * Position: x=617px (centered), y=34px
 * Chips: Search (30px icon) + Sort dropdown + Category filter
 * Spacing: 20px gap between chips
 * Dimensions: 64px height, 64px border-radius (pill shape)
 * Background: rgba(0,0,0,0.2) unfocused, rgba(0,0,0,0.4) focused
 * Font: Roboto Bold 20sp, line-height 28sp, #EEEEEE, tracking 0.4px
 */
@Composable
fun FilterChipRow(
    selectedSort: String,
    onSortChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateDown: () -> Unit,
    onNavigateRight: (() -> Boolean)?,
    onFocusReady: (FocusRequester) -> Unit,
    isFiltersFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier,
    categories: List<String>? = null  // Optional custom categories (defaults to TV channels)
) {
    val sortOptions = listOf("Po numerze", "Alfabetycznie", "Po kategorii")
    val categoryOptions = categories ?: listOf("Wszystkie", "Ogólne", "Sport", "Dzieci", "Dokumenty", "Filmy i seriale", "Informacyjne")

    var focusedChipIndex by remember { mutableStateOf(0) }
    val chipFocusRequesters = remember { List(3) { FocusRequester() } }

    // ✅ FOCUS ARCHITECT: Report first FocusRequester to parent ONCE
    LaunchedEffect(Unit) {
        onFocusReady(chipFocusRequesters[0])
    }

    // ✅ FOCUS ARCHITECT: Cleanup FocusRequesters on dispose
    DisposableEffect(Unit) {
        onDispose {
            chipFocusRequesters.forEach {
                try { it.freeFocus() } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    // Figma: chips at x=617px (centered), y=34px
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = sy(34), bottom = sy(34)),
        horizontalArrangement = Arrangement.spacedBy(sx(20), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search button (icon only, 30px)
        FilterChip(
            label = "",
            icon = {
                // Icon color matches TopMenu pattern
                val iconColor = if (focusedChipIndex == 0 && isFiltersFocused) {
                    Color(0xFF48227C)  // Purple when focused
                } else {
                    Color(0xFFEEEEEE)  // White otherwise
                }
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Wyszukaj",
                    tint = iconColor,
                    modifier = Modifier.size(sx(30), sy(30))
                )
            },
            onClick = onSearchClick,
            isFocused = focusedChipIndex == 0,
            focusRequester = chipFocusRequesters[0],
            onFocusChanged = { if (it) focusedChipIndex = 0 },
            onNavigate = { direction ->
                when (direction) {
                    ChipNavigation.LEFT -> {
                        // Wrap-around to last chip
                        focusedChipIndex = 2
                        chipFocusRequesters[2].requestFocus()
                        true
                    }
                    ChipNavigation.RIGHT -> {
                        focusedChipIndex = 1
                        chipFocusRequesters[1].requestFocus()
                        true
                    }
                    ChipNavigation.DOWN -> {
                        android.util.Log.d("FilterChipRow", "DOWN pressed on search chip - calling onNavigateDown")
                        onNavigateDown()
                        true
                    }
                }
            },
            onNavigateDown = onNavigateDown,
            isFiltersFocused = isFiltersFocused,
            sx = sx,
            sy = sy
        )

        // Sort dropdown chip
        FilterChip(
            label = "Sortuj: $selectedSort",
            onClick = {
                val currentIndex = sortOptions.indexOf(selectedSort)
                val nextIndex = (currentIndex + 1) % sortOptions.size
                onSortChange(sortOptions[nextIndex])
            },
            isFocused = focusedChipIndex == 1,
            focusRequester = chipFocusRequesters[1],
            onFocusChanged = { if (it) focusedChipIndex = 1 },
            onNavigate = { direction ->
                when (direction) {
                    ChipNavigation.LEFT -> {
                        focusedChipIndex = 0
                        chipFocusRequesters[0].requestFocus()
                        true
                    }
                    ChipNavigation.RIGHT -> {
                        focusedChipIndex = 2
                        chipFocusRequesters[2].requestFocus()
                        true
                    }
                    ChipNavigation.DOWN -> {
                        onNavigateDown()
                        true
                    }
                }
            },
            onNavigateDown = onNavigateDown,
            isFiltersFocused = isFiltersFocused,
            showChevron = true,
            sx = sx,
            sy = sy
        )

        // Category filter chip
        FilterChip(
            label = "Kategoria: $selectedCategory",
            onClick = {
                val currentIndex = categoryOptions.indexOf(selectedCategory)
                val nextIndex = (currentIndex + 1) % categoryOptions.size
                onCategoryChange(categoryOptions[nextIndex])
            },
            isFocused = focusedChipIndex == 2,
            focusRequester = chipFocusRequesters[2],
            onFocusChanged = { if (it) focusedChipIndex = 2 },
            onNavigate = { direction ->
                when (direction) {
                    ChipNavigation.LEFT -> {
                        focusedChipIndex = 1
                        chipFocusRequesters[1].requestFocus()
                        true
                    }
                    ChipNavigation.RIGHT -> {
                        // ✅ FOCUS ARCHITECT: Delegate to parent if available
                        val handled = onNavigateRight?.invoke() ?: false
                        if (!handled) {
                            // Wrap-around to first chip only if parent didn't consume
                            focusedChipIndex = 0
                            chipFocusRequesters[0].requestFocus()
                            true
                        } else {
                            handled
                        }
                    }
                    ChipNavigation.DOWN -> {
                        onNavigateDown()
                        true
                    }
                }
            },
            onNavigateDown = onNavigateDown,
            isFiltersFocused = isFiltersFocused,
            showChevron = true,
            sx = sx,
            sy = sy
        )
    }
}

private enum class ChipNavigation {
    LEFT, RIGHT, DOWN
}

@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onNavigate: (ChipNavigation) -> Boolean,
    onNavigateDown: (() -> Unit)?,
    isFiltersFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    icon: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false
) {
    // ✅ TOPMENU PATTERN: Aqua background + purple text when focused (no border)
    val backgroundColor = if (isFocused && isFiltersFocused) {
        Color(0xFF5AECD3)  // Aqua background (TopMenu pattern)
    } else {
        Color(0x0AEEEEEE)  // Almost transparent unfocused
    }

    val textColor = if (isFocused && isFiltersFocused) {
        Color(0xFF48227C)  // Purple text when focused
    } else {
        Color(0xFFEEEEEE)  // White text unfocused
    }

    Box(
        modifier = Modifier
            .height(sy(64))  // Figma: 64px height
            .clip(RoundedCornerShape(sx(64)))  // Figma: 64px border-radius (pill shape)
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> onNavigate(ChipNavigation.LEFT)
                        Key.DirectionRight -> onNavigate(ChipNavigation.RIGHT)
                        Key.DirectionDown -> onNavigate(ChipNavigation.DOWN)
                        Key.Enter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable()  // ✅ FOCUS ARCHITECT: Always focusable - parent controls when to use
            .padding(horizontal = sx(24), vertical = sy(12)),  // Figma: 24px horizontal, 12px vertical
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(8)),  // Figma: 8px gap
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                icon()
            }

            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    fontSize = (20 * sy(1).value / 1).sp,  // Figma: 20px
                    fontWeight = FontWeight.Bold,  // Figma: Manrope Bold → Roboto Bold
                    color = textColor,  // TopMenu pattern: purple when focused, white otherwise
                    textAlign = TextAlign.Center,
                    lineHeight = (28 * sy(1).value / 1).sp,  // Figma: line-height 28px
                    letterSpacing = 0.4.sp  // Figma: tracking 0.4px
                )
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textColor,  // TopMenu pattern: purple when focused, white otherwise
                    modifier = Modifier.size(sx(24), sy(24))  // Figma: 24x24px
                )
            }
        }
    }
}
