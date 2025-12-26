package com.spacetec.j2534

/**
 * Data class representing a J2534 device
 */
data class J2534Device(
    val handle: Long = 0L,
    val name: String = "",
    val vendor: String = "",
    val firmwareVersion: String = "",
    val dllVersion: String = "",
    val apiVersion: String = ""
)

/**
 * Data class representing a J2534 message
 */
data class J2534Message(
    var protocolID: Long = 0L,
    var rxStatus: Long = 0L,
    var txFlags: Long = 0L,
    var timestamp: Long = 0L,
    var data: ByteArray = byteArrayOf(),
    var extraDataIndex: Int = 0
)

/**
 * Data class representing a J2534 filter
 */
data class J2534Filter(
    val id: Long,
    val filterType: Long,
    val mask: J2534Message,
    val pattern: J2534Message,
    val flowControl: J2534Message?
)

/**
 * Data class representing a periodic message
 */
data class J2534PeriodicMessage(
    val id: Long,
    val message: J2534Message,
    val period: Long
)

/**
 * Interface for JNI wrapper
 */
interface J2534JniWrapperInterface {
    fun initialize(): Boolean
    fun scanForDevices(): List<J2534Device>
    fun connect(deviceHandle: Long, protocol: Long, flags: Long, baudrate: Long): Long
    fun disconnect(handle: Long): Long
    fun readMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long
    fun writeMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long
    fun startPeriodicMessage(handle: Long, message: J2534Message, id: Long, period: Long): Long
    fun stopPeriodicMessage(handle: Long, id: Long): Long
    fun startMessageFilter(
        handle: Long,
        filterType: Long,
        mask: J2534Message,
        pattern: J2534Message,
        flowControl: J2534Message?
    ): Long
    fun stopMessageFilter(handle: Long, filterId: Long): Long
    fun setProgrammingVoltage(handle: Long, pinNumber: Long, voltage: Long): Long
    fun readVersion(handle: Long, pApiVersion: StringBuilder, pDllVersion: StringBuilder, pDevVersion: StringBuilder): Long
    fun getLastError(): String
    fun ioctl(handle: Long, ioControlCode: Long, input: Long, output: Long): Long
    fun cleanup()
}

/**
 * JNI wrapper implementation
 */
class J2534JniWrapper : J2534JniWrapperInterface {
    companion object {
        init {
            System.loadLibrary("j2534_jni")
        }
    }

    external override fun initialize(): Boolean
    external override fun scanForDevices(): List<J2534Device>
    external override fun connect(deviceHandle: Long, protocol: Long, flags: Long, baudrate: Long): Long
    external override fun disconnect(handle: Long): Long
    external override fun readMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long
    external override fun writeMessages(handle: Long, messages: Array<J2534Message>, numMessages: Int, timeout: Long): Long
    external override fun startPeriodicMessage(handle: Long, message: J2534Message, id: Long, period: Long): Long
    external override fun stopPeriodicMessage(handle: Long, id: Long): Long
    external override fun startMessageFilter(
        handle: Long,
        filterType: Long,
        mask: J2534Message,
        pattern: J2534Message,
        flowControl: J2534Message?
    ): Long
    external override fun stopMessageFilter(handle: Long, filterId: Long): Long
    external override fun setProgrammingVoltage(handle: Long, pinNumber: Long, voltage: Long): Long
    external override fun readVersion(handle: Long, pApiVersion: StringBuilder, pDllVersion: StringBuilder, pDevVersion: StringBuilder): Long
    external override fun getLastError(): String
    external override fun ioctl(handle: Long, ioControlCode: Long, input: Long, output: Long): Long
    external override fun cleanup()
}