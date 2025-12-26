package com.spacetec.j2534

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/**
 * J2534Channel - Represents a communication channel with a J2534 device
 */
class J2534Channel(
    val handle: Long,
    private val manager: J2534Manager,
    val device: J2534Device,
    val protocol: Long
) {
    private val isConnected = AtomicBoolean(false)
    private val activeFilters = ConcurrentHashMap<Long, J2534Filter>()
    private val periodicMessages = ConcurrentHashMap<Long, J2534PeriodicMessage>()
    
    /**
     * Check if channel is connected
     */
    fun isConnected(): Boolean {
        return isConnected.get()
    }
    
    /**
     * Set connection status
     */
    fun setConnected(connected: Boolean) {
        isConnected.set(connected)
    }
    
    /**
     * Add an active filter to this channel
     */
    fun addFilter(filter: J2534Filter) {
        activeFilters[filter.id] = filter
    }
    
    /**
     * Remove a filter from this channel
     */
    fun removeFilter(filterId: Long) {
        activeFilters.remove(filterId)
    }
    
    /**
     * Get active filters count
     */
    fun getActiveFiltersCount(): Int {
        return activeFilters.size
    }
    
    /**
     * Add a periodic message to this channel
     */
    fun addPeriodicMessage(message: J2534PeriodicMessage) {
        periodicMessages[message.id] = message
    }
    
    /**
     * Remove a periodic message from this channel
     */
    fun removePeriodicMessage(id: Long) {
        periodicMessages.remove(id)
    }
    
    /**
     * Get periodic messages count
     */
    fun getPeriodicMessagesCount(): Int {
        return periodicMessages.size
    }
    
    /**
     * Read messages from this channel
     */
    fun readMessages(messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        return manager.readMessages(handle, messages, numMessages, timeout)
    }
    
    /**
     * Write messages to this channel
     */
    fun writeMessages(messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        return manager.writeMessages(handle, messages, numMessages, timeout)
    }
    
    /**
     * Start a periodic message on this channel
     */
    fun startPeriodicMessage(message: J2534Message, id: Long, period: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        val result = manager.startPeriodicMessage(handle, message, id, period)
        if (result == J2534Errors.STATUS_NOERROR) {
            addPeriodicMessage(J2534PeriodicMessage(id, message, period))
        }
        return result
    }
    
    /**
     * Stop a periodic message on this channel
     */
    fun stopPeriodicMessage(id: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        val result = manager.stopPeriodicMessage(handle, id)
        if (result == J2534Errors.STATUS_NOERROR) {
            removePeriodicMessage(id)
        }
        return result
    }
    
    /**
     * Start a message filter on this channel
     */
    fun startMessageFilter(
        filterType: Long,
        mask: J2534Message,
        pattern: J2534Message,
        flowControl: J2534Message?
    ): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        val result = manager.startMessageFilter(handle, filterType, mask, pattern, flowControl)
        if (result == J2534Errors.STATUS_NOERROR) {
            val filter = J2534Filter(result, filterType, mask, pattern, flowControl)
            addFilter(filter)
        }
        return result
    }
    
    /**
     * Stop a message filter on this channel
     */
    fun stopMessageFilter(filterId: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        val result = manager.stopMessageFilter(handle, filterId)
        if (result == J2534Errors.STATUS_NOERROR) {
            removeFilter(filterId)
        }
        return result
    }
    
    /**
     * Perform IOCTL operation on this channel
     */
    fun ioctl(ioControlCode: Long, input: Long, output: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        return manager.ioctl(handle, ioControlCode, input, output)
    }
    
    /**
     * Set programming voltage through this channel
     */
    fun setProgrammingVoltage(pinNumber: Long, voltage: Long): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_DEVICE_NOT_CONNECTED
        }
        
        return manager.setProgrammingVoltage(handle, pinNumber, voltage)
    }
    
    /**
     * Close this channel
     */
    fun close(): Long {
        if (!isConnected.get()) {
            return J2534Errors.STATUS_NOERROR
        }
        
        // Stop all periodic messages
        periodicMessages.forEach { (id, _) ->
            stopPeriodicMessage(id)
        }
        
        // Stop all filters
        activeFilters.forEach { (id, _) ->
            stopMessageFilter(id)
        }
        
        // Disconnect from manager
        val result = manager.disconnect(handle)
        if (result == J2534Errors.STATUS_NOERROR) {
            isConnected.set(false)
        }
        
        return result
    }
    
    /**
     * Send UDS request through this channel
     */
    fun sendUdsRequest(serviceId: Byte, data: ByteArray, timeout: Long = 2000L): J2534Message? {
        return manager.sendUdsRequest(handle, serviceId, data, timeout)
    }
    
    /**
     * Send raw CAN message through this channel
     */
    fun sendCanMessage(canId: Long, data: ByteArray, timeout: Long = 1000L): Long {
        val message = J2534Message().apply {
            protocolID = J2534Protocols.CAN
            txFlags = if (canId > 0x7FF) 0x0100L /* CAN_29BIT_ID */ else 0L // Extended CAN ID
            data = if (canId > 0x7FF) {
                // For 29-bit CAN ID, format as 4-byte ID + data
                byteArrayOf(
                    ((canId shr 24) and 0xFF).toByte(),
                    ((canId shr 16) and 0xFF).toByte(),
                    ((canId shr 8) and 0xFF).toByte(),
                    (canId and 0xFF).toByte()
                ) + data
            } else {
                // For 11-bit CAN ID, format as 2-byte ID + data
                byteArrayOf(
                    ((canId shr 8) and 0xFF).toByte(),
                    (canId and 0xFF).toByte()
                ) + data
            }
        }
        
        val messages = arrayOf(message)
        return writeMessages(messages, 1, timeout)
    }
}