package com.spacetec.j2534

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * J2534Manager - Main entry point for J2534 API implementation
 * Manages devices, channels, and provides thread-safe access to J2534 functionality
 */
class J2534Manager {
    private val _deviceState = MutableStateFlow<J2534DeviceState>(J2534DeviceState.Disconnected)
    val deviceState: StateFlow<J2534DeviceState> = _deviceState.asStateFlow()

    // Thread-safe collections for managing resources
    private val openChannels = ConcurrentHashMap<Long, J2534Channel>()
    private val activeFilters = ConcurrentHashMap<Long, J2534Filter>()
    private val periodicMessages = ConcurrentHashMap<Long, J2534PeriodicMessage>()

    // Thread safety for resource management
    private val resourceLock = ReentrantReadWriteLock()

    // JNI interface
    private val jniInterface = J2534JniWrapper()

    // Device state tracking
    private val isInitialized = AtomicBoolean(false)
    private var activeDevice: J2534Device? = null

    /**
     * Device connection state enum
     */
    enum class J2534DeviceState {
        Disconnected,
        Connected,
        Initializing,
        Error
    }

    /**
     * Initialize the J2534 manager
     */
    fun initialize(): Boolean {
        if (isInitialized.get()) return true
        
        return try {
            jniInterface.initialize()
            isInitialized.set(true)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Scan for available J2534 devices
     */
    fun scanForDevices(): List<J2534Device> {
        if (!isInitialized.get()) return emptyList()
        
        return jniInterface.scanForDevices()
    }

    /**
     * Connect to a J2534 device
     */
    fun connect(device: J2534Device, protocol: Long, flags: Long, baudrate: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            try {
                val handle = jniInterface.connect(device.handle, protocol, flags, baudrate)
                if (handle != J2534Errors.STATUS_NOERROR) {
                    val channel = J2534Channel(handle, this, device, protocol)
                    openChannels[handle] = channel
                    activeDevice.set(device)
                    _deviceState.value = J2534DeviceState.Connected
                    return handle
                }
                return handle
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_DEVICE_ID
            }
        }
    }

    /**
     * Close a connection to a J2534 device
     */
    fun disconnect(handle: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            try {
                val result = jniInterface.disconnect(handle)
                if (result == J2534Errors.STATUS_NOERROR) {
                    openChannels.remove(handle)
                    if (openChannels.isEmpty()) {
                        _deviceState.value = J2534DeviceState.Disconnected
                        activeDevice.set(null)
                    }
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
        }
    }

    /**
     * Read messages from a channel
     */
    fun readMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.read {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                return jniInterface.readMessages(handle, messages, numMessages, timeout)
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_BUFFER_EMPTY
            }
        }
    }

    /**
     * Write messages to a channel
     */
    fun writeMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.read {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                return jniInterface.writeMessages(handle, messages, numMessages, timeout)
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_BUFFER_OVERFLOW
            }
        }
    }

    /**
     * Start a periodic message
     */
    fun startPeriodicMessage(handle: Long, message: J2534Message, id: Long, period: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                val result = jniInterface.startPeriodicMessage(handle, message, id, period)
                if (result == J2534Errors.STATUS_NOERROR) {
                    periodicMessages[id] = J2534PeriodicMessage(id, message, period)
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_MSG
            }
        }
    }

    /**
     * Stop a periodic message
     */
    fun stopPeriodicMessage(handle: Long, id: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                val result = jniInterface.stopPeriodicMessage(handle, id)
                if (result == J2534Errors.STATUS_NOERROR) {
                    periodicMessages.remove(id)
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_MSG_ID
            }
        }
    }

    /**
     * Start a message filter
     */
    fun startMessageFilter(
        handle: Long,
        filterType: Long,
        mask: J2534Message,
        pattern: J2534Message,
        flowControl: J2534Message?
    ): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                val result = jniInterface.startMessageFilter(handle, filterType, mask, pattern, flowControl)
                if (result == J2534Errors.STATUS_NOERROR) {
                    val filter = J2534Filter(result, filterType, mask, pattern, flowControl)
                    activeFilters[result] = filter
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_FILTER_ID
            }
        }
    }

    /**
     * Stop a message filter
     */
    fun stopMessageFilter(handle: Long, filterId: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.write {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                val result = jniInterface.stopMessageFilter(handle, filterId)
                if (result == J2534Errors.STATUS_NOERROR) {
                    activeFilters.remove(filterId)
                }
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_FILTER_ID
            }
        }
    }

    /**
     * Set programming voltage
     */
    fun setProgrammingVoltage(handle: Long, pinNumber: Long, voltage: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.read {
            try {
                return jniInterface.setProgrammingVoltage(handle, pinNumber, voltage)
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_IOCTL_VALUE
            }
        }
    }

    /**
     * Read version information
     */
    fun readVersion(handle: Long, pApiVersion: StringBuilder, pDllVersion: StringBuilder, pDevVersion: StringBuilder): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.read {
            try {
                return jniInterface.readVersion(handle, pApiVersion, pDllVersion, pDevVersion)
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
        }
    }

    /**
     * Get last error message
     */
    fun getLastError(): String {
        return jniInterface.getLastError()
    }

    /**
     * Perform IOCTL operation
     */
    fun ioctl(handle: Long, ioControlCode: Long, input: Long, output: Long): Long {
        if (!isInitialized.get()) return J2534Errors.STATUS_NOT_SUPPORTED
        
        resourceLock.read {
            if (!openChannels.containsKey(handle)) {
                return J2534Errors.STATUS_INVALID_CHANNEL_ID
            }
            
            try {
                return jniInterface.ioctl(handle, ioControlCode, input, output)
            } catch (e: Exception) {
                e.printStackTrace()
                return J2534Errors.STATUS_INVALID_IOCTL_ID
            }
        }
    }

    /**
     * Helper method for UDS communication
     */
    fun sendUdsRequest(handle: Long, serviceId: Byte, data: ByteArray, timeout: Long = 2000L): J2534Message? {
        val request = J2534Message().apply {
            protocolID = J2534Protocols.ISO15765
            data = byteArrayOf(0x0C, 0xF0, 0x00) // Example CAN ID for UDS
            data += serviceId
            data += data
        }
        
        val response = J2534Message()
        val messages = arrayOf(request)
        val responses = arrayOf(response)
        
        // Write the request
        val writeResult = writeMessages(handle, messages, 1, timeout)
        if (writeResult != J2534Errors.STATUS_NOERROR) {
            return null
        }
        
        // Read the response
        val readResult = readMessages(handle, responses, 1, timeout)
        if (readResult != J2534Errors.STATUS_NOERROR) {
            return null
        }
        
        return responses[0]
    }

    /**
     * Get active channels count
     */
    fun getActiveChannelsCount(): Int {
        return openChannels.size
    }

    /**
     * Get active filters count
     */
    fun getActiveFiltersCount(): Int {
        return activeFilters.size
    }

    /**
     * Get periodic messages count
     */
    fun getPeriodicMessagesCount(): Int {
        return periodicMessages.size
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        resourceLock.write {
            // Stop all periodic messages
            periodicMessages.forEach { (id, _) ->
                stopPeriodicMessage(openChannels.entries.firstOrNull()?.key ?: 0L, id)
            }
            
            // Stop all filters
            activeFilters.forEach { (id, _) ->
                openChannels.entries.forEach { (handle, _) ->
                    stopMessageFilter(handle, id)
                }
            }
            
            // Close all channels
            openChannels.forEach { (handle, _) ->
                disconnect(handle)
            }
            
            jniInterface.cleanup()
            isInitialized.set(false)
            _deviceState.value = J2534DeviceState.Disconnected
        }
    }

    companion object {
        @JvmStatic
        private val instance: J2534Manager by lazy { J2534Manager() }
        
        @JvmStatic
        fun getInstance(): J2534Manager = instance
    }
}