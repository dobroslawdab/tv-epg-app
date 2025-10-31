package com.uxellence.tv.v3

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.repository.EpgRepository

/**
 * ZAPPING BAR OVERLAY
 *
 * Composable orchestrator for different Zapping Bar display modes:
 * - NumberEntry: Shows only ChannelNumberCard (user typing digits)
 * - ShowingChannel: Shows full Zapping Bar (after channel confirmed)
 * - InvalidChannel: Shows error message
 * - ChannelUp/Down: Shows full Zapping Bar for current channel
 *
 * Focus Architect compliance:
 * - No key handling (parent handles keys)
 * - Only visual display based on ZappingMode
 * - onDismiss callback for BACK delegation
 */
@Composable
fun ZappingBarOverlay(
    mode: ZappingMode,
    epgRepository: EpgRepository?,
    onDismiss: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    when (mode) {
        // User is typing channel number - show only number card
        is ZappingMode.NumberEntry -> {
            NumberEntryDisplay(
                digits = mode.digits,
                sx = sx,
                sy = sy
            )
        }

        // Channel confirmed - show full zapping bar
        is ZappingMode.ShowingChannel -> {
            val data = mode.channel.toZappingBarData(
                program = mode.program,
                epgRepository = epgRepository
            )

            ZappingBarScreen(
                onBackPressed = onDismiss,
                sx = sx,
                sy = sy,
                data = data
            )
        }

        // Invalid channel number - show error
        is ZappingMode.InvalidChannel -> {
            InvalidChannelDisplay(
                number = mode.number,
                sx = sx,
                sy = sy
            )
        }

        // Channel UP pressed - show full zapping bar with channel data
        is ZappingMode.ChannelUp -> {
            val data = mode.channel.toZappingBarData(
                program = mode.program,
                epgRepository = epgRepository
            )

            ZappingBarScreen(
                onBackPressed = onDismiss,
                sx = sx,
                sy = sy,
                data = data
            )
        }

        // Channel DOWN pressed - show full zapping bar with channel data
        is ZappingMode.ChannelDown -> {
            val data = mode.channel.toZappingBarData(
                program = mode.program,
                epgRepository = epgRepository
            )

            ZappingBarScreen(
                onBackPressed = onDismiss,
                sx = sx,
                sy = sy,
                data = data
            )
        }
    }
}
