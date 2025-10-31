package com.uxellence.tv.v3

import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.channels.TvChannelData
import com.uxellence.tv.v3.epg.EpgProgram
import com.uxellence.tv.v3.repository.EpgRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PLAYER INTERFACE MANAGER
 *
 * State machine managing dual interface system:
 * - GUI Interface (UP/DOWN/OK) - 10 second auto-hide
 * - Zapping Bar (CH+/CH-/digits) - 5 second auto-hide
 *
 * Interfaces are mutually exclusive:
 * - GUI visible + CH+ → Hide GUI, Show Zapping Bar
 * - Zapping Bar visible + UP → Hide Zapping Bar, Show GUI
 *
 * Focus Architect compliance:
 * - Level: Player Component
 * - Pattern: State-based mutual exclusion
 * - No conflicts: Only one interface visible at a time
 */

/**
 * Player interface state
 */
sealed class PlayerInterfaceState {
    /** Both interfaces hidden (player only) */
    object Hidden : PlayerInterfaceState()

    /** Full GUI interface visible (current + 3 other channels) */
    object GuiVisible : PlayerInterfaceState()

    /** Zapping Bar visible (minimalistic channel info) */
    data class ZappingBarVisible(
        val mode: ZappingMode
    ) : PlayerInterfaceState()
}

/**
 * Zapping Bar display modes
 */
sealed class ZappingMode {
    /** User pressed CH+ */
    data class ChannelUp(
        val channel: TvChannelData,
        val program: EpgProgram?
    ) : ZappingMode()

    /** User pressed CH- */
    data class ChannelDown(
        val channel: TvChannelData,
        val program: EpgProgram?
    ) : ZappingMode()

    /** User is entering channel number (1, 12, 123...) */
    data class NumberEntry(val digits: String) : ZappingMode()

    /** Channel confirmed - showing full info */
    data class ShowingChannel(
        val channel: TvChannelData,
        val program: EpgProgram?
    ) : ZappingMode()

    /** Invalid channel number entered */
    data class InvalidChannel(val number: String) : ZappingMode()
}

/**
 * Singleton manager for player interface state
 */
object PlayerInterfaceManager {

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Main)

    // Current interface state
    private val _state = MutableStateFlow<PlayerInterfaceState>(PlayerInterfaceState.Hidden)
    val state: StateFlow<PlayerInterfaceState> = _state.asStateFlow()

    // Auto-hide timers
    private var autoHideJob: Job? = null
    private var digitTimeoutJob: Job? = null

    // Current channel tracking
    private var currentChannelIndex: Int = 0

    // Callback for channel changes (notify player to switch stream)
    var onChannelChange: ((TvChannelData) -> Unit)? = null

    // EpgRepository instance (set from outside)
    var epgRepository: EpgRepository? = null

    /**
     * Initialize with current channel
     */
    fun initialize(channelId: String?) {
        if (channelId != null) {
            val channels = ChannelManager.getAllChannels()
            currentChannelIndex = channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)
        }
    }

    // ==================== GUI TRIGGERS ====================

    /**
     * Show full GUI interface (UP/DOWN/OK pressed)
     * Auto-hide after 10 seconds
     */
    fun showGui() {
        hideZappingBarIfVisible() // Mutual exclusion
        _state.value = PlayerInterfaceState.GuiVisible
        autoHide(10_000) // 10 seconds
    }

    // ==================== ZAPPING TRIGGERS ====================

    /**
     * Channel UP (CH+ button)
     */
    fun channelUp(program: EpgProgram? = null) {
        hideGuiIfVisible() // Mutual exclusion

        val channels = ChannelManager.getAllChannels()
        if (channels.isEmpty()) return

        currentChannelIndex = (currentChannelIndex + 1) % channels.size
        val nextChannel = channels[currentChannelIndex]

        _state.value = PlayerInterfaceState.ZappingBarVisible(
            ZappingMode.ChannelUp(channel = nextChannel, program = program)
        )

        switchToChannel(nextChannel)
        autoHide(5_000) // 5 seconds
    }

    /**
     * Channel DOWN (CH- button)
     */
    fun channelDown(program: EpgProgram? = null) {
        hideGuiIfVisible() // Mutual exclusion

        val channels = ChannelManager.getAllChannels()
        if (channels.isEmpty()) return

        currentChannelIndex = if (currentChannelIndex == 0) {
            channels.size - 1
        } else {
            currentChannelIndex - 1
        }
        val prevChannel = channels[currentChannelIndex]

        _state.value = PlayerInterfaceState.ZappingBarVisible(
            ZappingMode.ChannelDown(channel = prevChannel, program = program)
        )

        switchToChannel(prevChannel)
        autoHide(5_000) // 5 seconds
    }

    /**
     * User entered a digit (0-9)
     * Accumulates digits and waits 2 seconds before confirming
     */
    fun appendDigit(digit: Int) {
        hideGuiIfVisible() // Mutual exclusion

        // Get current digits (if already in NumberEntry mode)
        val current = when (val s = _state.value) {
            is PlayerInterfaceState.ZappingBarVisible -> {
                when (val mode = s.mode) {
                    is ZappingMode.NumberEntry -> mode.digits
                    else -> ""
                }
            }
            else -> ""
        }

        // Append new digit (max 3 digits for channel numbers like 999)
        val newDigits = if (current.length < 3) {
            current + digit.toString()
        } else {
            current // Ignore if already 3 digits
        }

        // Update state to show number entry
        _state.value = PlayerInterfaceState.ZappingBarVisible(
            ZappingMode.NumberEntry(newDigits)
        )

        // Reset 2-second timeout
        digitTimeoutJob?.cancel()
        digitTimeoutJob = scope.launch {
            delay(2_000) // 2 seconds
            confirmChannelNumber(newDigits)
        }
    }

    /**
     * Confirm channel number after timeout
     */
    private fun confirmChannelNumber(number: String) {
        val channelNum = number.toIntOrNull() ?: return
        val channel = ChannelManager.getChannelByNumber(channelNum)

        if (channel != null) {
            // Valid channel - show full zapping bar
            val program = epgRepository?.getCurrentProgramSync(channel.id)

            _state.value = PlayerInterfaceState.ZappingBarVisible(
                ZappingMode.ShowingChannel(channel, program)
            )

            // Update current channel index
            val channels = ChannelManager.getAllChannels()
            currentChannelIndex = channels.indexOf(channel).coerceAtLeast(0)

            switchToChannel(channel)
            autoHide(5_000) // 5 seconds
        } else {
            // Invalid channel - show error
            _state.value = PlayerInterfaceState.ZappingBarVisible(
                ZappingMode.InvalidChannel(number)
            )
            autoHide(5_000) // 5 seconds
        }
    }

    // ==================== CHANNEL SWITCHING ====================

    /**
     * Notify player to switch to channel
     */
    private fun switchToChannel(channel: TvChannelData) {
        onChannelChange?.invoke(channel)
    }

    /**
     * Get current channel
     */
    fun getCurrentChannel(): TvChannelData? {
        val channels = ChannelManager.getAllChannels()
        return channels.getOrNull(currentChannelIndex)
    }

    // ==================== STATE MANAGEMENT ====================

    /**
     * Hide interface (BACK pressed or auto-hide timeout)
     */
    fun hide() {
        _state.value = PlayerInterfaceState.Hidden
        autoHideJob?.cancel()
        digitTimeoutJob?.cancel()
    }

    /**
     * Check if any interface is visible
     */
    fun isVisible(): Boolean {
        return _state.value != PlayerInterfaceState.Hidden
    }

    /**
     * Show Zapping Bar with specific channel and program data
     * Used by EpgDayScreen to show Zapping Bar with EPG data
     */
    fun showZappingBar(mode: ZappingMode) {
        hideGuiIfVisible() // Mutual exclusion
        _state.value = PlayerInterfaceState.ZappingBarVisible(mode)
        autoHide(5_000) // 5 seconds
    }

    /**
     * Hide GUI if visible (mutual exclusion helper)
     */
    private fun hideGuiIfVisible() {
        if (_state.value is PlayerInterfaceState.GuiVisible) {
            _state.value = PlayerInterfaceState.Hidden
            autoHideJob?.cancel()
        }
    }

    /**
     * Hide Zapping Bar if visible (mutual exclusion helper)
     */
    private fun hideZappingBarIfVisible() {
        if (_state.value is PlayerInterfaceState.ZappingBarVisible) {
            _state.value = PlayerInterfaceState.Hidden
            autoHideJob?.cancel()
            digitTimeoutJob?.cancel()
        }
    }

    /**
     * Auto-hide after delay
     */
    private fun autoHide(delayMs: Long) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(delayMs)
            _state.value = PlayerInterfaceState.Hidden
        }
    }

    /**
     * Reset state (cleanup)
     */
    fun reset() {
        _state.value = PlayerInterfaceState.Hidden
        autoHideJob?.cancel()
        digitTimeoutJob?.cancel()
        currentChannelIndex = 0
        onChannelChange = null
        epgRepository = null
    }
}

/**
 * Extension: Get current program synchronously (blocking)
 * Used for quick zapping without coroutines
 */
private fun EpgRepository.getCurrentProgramSync(channelId: String): EpgProgram? {
    return try {
        // Try to get from cache if available
        // This is a simplified version - in production you'd use proper suspend functions
        null // Placeholder - implement proper sync access if needed
    } catch (e: Exception) {
        null
    }
}
