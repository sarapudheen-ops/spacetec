/**
 * TimingParameters.kt
 *
 * Defines timing parameters for diagnostic protocols.
 */

package com.spacetec.protocol.core.timing

/**
 * Timing parameters for diagnostic communication.
 */
data class TimingParameters(
    val connectTimeoutMs: Long = 5000L,
    val sendTimeoutMs: Long = 1000L,
    val receiveTimeoutMs: Long = 1000L,
    val sessionTimeoutMs: Long = 50000L,
    val defaultTimeoutMs: Long = 1000L,
    val interByteTimeoutMs: Long = 50L,
    val keepAliveIntervalMs: Long = 2000L
) {
    companion object {
        val DEFAULT = TimingParameters()
    }
}