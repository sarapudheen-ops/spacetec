package com.obdreader.data.obd.commands

/**
 * Base class for OBD-II commands.
 */
abstract class OBDCommand(
    val mode: Int,
    val pid: Int? = null,
    val name: String,
    val description: String
) {
    /**
     * Get the command string to send to the adapter.
     */
    open fun getCommand(): String {
        return if (pid != null) {
            "${mode.toString(16).uppercase().padStart(2, '0')}${pid.toString(16).uppercase().padStart(2, '0')}"
        } else {
            mode.toString(16).uppercase().padStart(2, '0')
        }
    }
    
    /**
     * Get expected response mode.
     */
    fun getResponseMode(): Int = mode + 0x40
}
