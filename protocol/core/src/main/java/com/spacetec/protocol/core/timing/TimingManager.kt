/**
 * TimingManager.kt
 *
 * Manages timing parameters for diagnostic protocols.
 * Handles timeouts, delays, and timing requirements for different protocols.
 */

package com.spacetec.protocol.core.timing

/**
 * Manages timing parameters for diagnostic communication.
 *
 * Provides protocol-specific timing configurations and utilities
 * for managing timeouts, inter-byte delays, and session timing.
 */
class TimingManager {

    /**
     * Current timing parameters.
     */
    var parameters: TimingParameters = TimingParameters.DEFAULT
        private set

    /**
     * Updates the timing parameters.
     */
    fun updateParameters(newParameters: TimingParameters) {
        parameters = newParameters
    }

    /**
     * Resets timing parameters to defaults.
     */
    fun reset() {
        parameters = TimingParameters.DEFAULT
    }

    /**
     * Gets the appropriate timeout for an operation.
     */
    fun getTimeout(operation: String): Long = when (operation) {
        "connect" -> parameters.connectTimeoutMs
        "send" -> parameters.sendTimeoutMs
        "receive" -> parameters.receiveTimeoutMs
        "session" -> parameters.sessionTimeoutMs
        else -> parameters.defaultTimeoutMs
    }

    companion object {
        /**
         * Default timing manager instance.
         */
        val DEFAULT = TimingManager()
    }
}