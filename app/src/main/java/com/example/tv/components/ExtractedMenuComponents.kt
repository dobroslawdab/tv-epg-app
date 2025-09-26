package com.example.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.tv.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Pure rendering components extracted from ReusableTopMenu
 * These components handle only UI rendering without any focus management logic
 * Focus management is handled externally by the parent component
 */

/**
 * Pure TopMenuBar component - rendering only, no focus logic
 */
@Composable
fun PureTopMenuBar(
    menuItems: List<MenuItem>,
    selectedItemId: String,
    focusedItemId: String,
    focusRequesters: Map<String, FocusRequester>,
    onMenuItemFocused: (String) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sy(97))
            .padding(horizontal = sx(40), vertical = sy(20))
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(sx(40)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            itemsIndexed(menuItems) { index, item ->
                val isSelected = item.id == selectedItemId
                val isFocused = item.id == focusedItemId
                val focusRequester = focusRequesters[item.id] ?: FocusRequester()
                
                PureMenuTabItem(
                    item = item,
                    isSelected = isSelected,
                    isFocused = isFocused,
                    focusRequester = focusRequester,
                    onFocusChanged = { focused ->
                        if (focused) {
                            onMenuItemFocused(item.id)
                        }
                    },
                    sx = sx,
                    sy = sy
                )
            }
        }
    }
}

/**
 * Pure menu tab item - rendering only
 */
@Composable
private fun PureMenuTabItem(
    item: MenuItem,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val textColor = when {
        isFocused -> Color(0xFF48227C) // Purple when focused
        isSelected -> Color(0xFF48227C) // Purple when selected  
        else -> Color(0xFFEEEEEE) // White when normal
    }
    
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3) // Aqua background when focused
        isSelected -> Color.White // White background when selected
        else -> Color(0x0DEEEEEE) // Semi-transparent white when normal
    }
    
    Box(
        modifier = Modifier
            .height(sy(80)) // Same as original TopMenuScreen
            .background(backgroundColor, CircleShape) // Same shape as original
            .padding(horizontal = sx(32)) // Same padding as original
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.title,
            color = textColor,
            fontSize = sy(24).value.sp, // Same font size as original
            fontWeight = FontWeight.Medium, // Same weight as original
            lineHeight = sy(32).value.sp, // Same line height as original
            letterSpacing = 0.48.sp, // Same letter spacing as original
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Pure RightMenuBar component - rendering only, no focus logic
 */
@Composable
fun PureRightMenuBar(
    notificationsFocused: Boolean,
    settingsFocused: Boolean,
    notificationsFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onNotificationsFocusChanged: (Boolean) -> Unit,
    onSettingsFocusChanged: (Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = sy(20), end = sx(30))
            .height(sy(97)),
        horizontalArrangement = Arrangement.spacedBy(sx(30)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icons section with 10px gap
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notifications icon
            PureRightMenuIcon(
                icon = Icons.Default.Notifications,
                contentDescription = "Notifications",
                isFocused = notificationsFocused,
                focusRequester = notificationsFocusRequester,
                onFocusChanged = onNotificationsFocusChanged,
                sx = sx,
                sy = sy
            )
            
            // Settings icon
            PureRightMenuIcon(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                isFocused = settingsFocused,
                focusRequester = settingsFocusRequester,
                onFocusChanged = onSettingsFocusChanged,
                sx = sx,
                sy = sy
            )
        }
        
        // Digital Clock
        PureDigitalClock(sx = sx, sy = sy)
    }
}

/**
 * Pure right menu icon - rendering only
 */
@Composable
private fun PureRightMenuIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x33EEEEEE) // Same as original TopMenuScreen
    val iconColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE) // Same as original TopMenuScreen
    
    Box(
        modifier = Modifier
            .size(sx(57))
            .background(backgroundColor, CircleShape)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(sx(24))
        )
    }
}

/**
 * Pure digital clock component - rendering only
 */
@Composable
private fun PureDigitalClock(
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val formattedTime = currentTime.format(timeFormatter)
    val (hours, minutes) = formattedTime.split(":")
    
    // Blinking colon state
    var showColon by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            showColon = !showColon
        }
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Hours
        Text(
            text = hours,
            color = Color(0xFFEEEEEE),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp
        )
        
        // Colon (with blinking effect using alpha)
        Text(
            text = ":",
            color = Color(0xFFEEEEEE).copy(alpha = if (showColon) 1f else 0f),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp,
            modifier = Modifier.width(sx(8)) // Fixed width to prevent shifting
        )
        
        // Minutes
        Text(
            text = minutes,
            color = Color(0xFFEEEEEE),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp
        )
    }
}