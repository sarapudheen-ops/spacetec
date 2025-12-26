package com.spacetec.j2534.api

import com.spacetec.j2534.J2534Errors
import com.spacetec.j2534.J2534Message

/**
 * Interface defining the core PassThru API functions as per SAE J2534 standard
 */
interface PassThruApi {
    /**
     * PassThruOpen - Opens a connection to a J2534 device
     * @param pDeviceID Pointer to receive the device ID
     * @return Error code
     */
    fun passThruOpen(pDeviceID: LongArray): Long
    
    /**
     * PassThruClose - Closes a connection to a J2534 device
     * @param DeviceID The device ID to close
     * @return Error code
     */
    fun passThruClose(DeviceID: Long): Long
    
    /**
     * PassThruConnect - Establishes a communication channel with a vehicle protocol
     * @param DeviceID The device ID
     * @param ProtocolID The protocol to use
     * @param Flags Protocol-specific flags
     * @param Baudrate The baud rate for the connection
     * @param pChannelID Pointer to receive the channel ID
     * @return Error code
     */
    fun passThruConnect(
        DeviceID: Long,
        ProtocolID: Long,
        Flags: Long,
        Baudrate: Long,
        pChannelID: LongArray
    ): Long
    
    /**
     * PassThruDisconnect - Terminates a communication channel
     * @param ChannelID The channel ID to disconnect
     * @return Error code
     */
    fun passThruDisconnect(ChannelID: Long): Long
    
    /**
     * PassThruReadMsgs - Reads messages from a communication channel
     * @param ChannelID The channel ID
     * @param pMsg Array of message structures to fill
     * @param pNumMsgs Pointer to number of messages to read / number of messages read
     * @param Timeout Time to wait for messages in milliseconds
     * @return Error code
     */
    fun passThruReadMsgs(
        ChannelID: Long,
        pMsg: Array<J2534Message>,
        pNumMsgs: IntArray,
        Timeout: Long
    ): Long
    
    /**
     * PassThruWriteMsgs - Writes messages to a communication channel
     * @param ChannelID The channel ID
     * @param pMsg Array of message structures to write
     * @param pNumMsgs Pointer to number of messages to write / number of messages written
     * @param Timeout Time to wait for transmission in milliseconds
     * @return Error code
     */
    fun passThruWriteMsgs(
        ChannelID: Long,
        pMsg: Array<J2534Message>,
        pNumMsgs: IntArray,
        Timeout: Long
    ): Long
    
    /**
     * PassThruStartPeriodicMsg - Starts transmission of a message at regular intervals
     * @param ChannelID The channel ID
     * @param pMsg Pointer to message structure to send periodically
     * @param pMsgID Pointer to receive the message ID
     * @param TimeInterval Interval between transmissions in milliseconds
     * @return Error code
     */
    fun passThruStartPeriodicMsg(
        ChannelID: Long,
        pMsg: J2534Message,
        pMsgID: LongArray,
        TimeInterval: Long
    ): Long
    
    /**
     * PassThruStopPeriodicMsg - Stops transmission of a periodic message
     * @param ChannelID The channel ID
     * @param MsgID The message ID to stop
     * @return Error code
     */
    fun passThruStopPeriodicMsg(ChannelID: Long, MsgID: Long): Long
    
    /**
     * PassThruStartMsgFilter - Creates a message filter
     * @param ChannelID The channel ID
     * @param FilterType Type of filter to create
     * @param pMask Pointer to mask structure
     * @param pPattern Pointer to pattern structure
     * @param pFlowControlData Pointer to flow control data (optional)
     * @param pFilterID Pointer to receive the filter ID
     * @return Error code
     */
    fun passThruStartMsgFilter(
        ChannelID: Long,
        FilterType: Long,
        pMask: J2534Message,
        pPattern: J2534Message,
        pFlowControlData: J2534Message?,
        pFilterID: LongArray
    ): Long
    
    /**
     * PassThruStopMsgFilter - Removes a message filter
     * @param ChannelID The channel ID
     * @param FilterID The filter ID to remove
     * @return Error code
     */
    fun passThruStopMsgFilter(ChannelID: Long, FilterID: Long): Long
    
    /**
     * PassThruSetProgrammingVoltage - Sets programming voltage on a pin
     * @param DeviceID The device ID
     * @param PinNumber The J1962 pin number
     * @param Voltage The voltage in millivolts (0 = off, 12000 = 12V, etc.)
     * @return Error code
     */
    fun passThruSetProgrammingVoltage(DeviceID: Long, PinNumber: Long, Voltage: Long): Long
    
    /**
     * PassThruReadVersion - Reads version information
     * @param DeviceID The device ID
     * @param pApiVersion Pointer to buffer for API version
     * @param pDllVersion Pointer to buffer for DLL version
     * @param pDevName Pointer to buffer for device name
     * @return Error code
     */
    fun passThruReadVersion(
        DeviceID: Long,
        pApiVersion: StringBuilder,
        pDllVersion: StringBuilder,
        pDevName: StringBuilder
    ): Long
    
    /**
     * PassThruGetLastError - Gets the last error message
     * @param pErrorDescription Pointer to buffer for error description
     * @return Error code
     */
    fun passThruGetLastError(pErrorDescription: StringBuilder): Long
    
    /**
     * PassThruIoctl - Performs various control operations
     * @param Handle Channel ID or Device ID depending on IoctlID
     * @param IoctlID The IOCTL operation to perform
     * @param pInput Pointer to input data
     * @param pOutput Pointer to output data
     * @return Error code
     */
    fun passThruIoctl(Handle: Long, IoctlID: Long, pInput: Long, pOutput: Long): Long
}

/**
 * Default implementation of PassThru API functions
 */
class PassThruApiImpl : PassThruApi {
    override fun passThruOpen(pDeviceID: LongArray): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruClose(DeviceID: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruConnect(
        DeviceID: Long,
        ProtocolID: Long,
        Flags: Long,
        Baudrate: Long,
        pChannelID: LongArray
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruDisconnect(ChannelID: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruReadMsgs(
        ChannelID: Long,
        pMsg: Array<J2534Message>,
        pNumMsgs: IntArray,
        Timeout: Long
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruWriteMsgs(
        ChannelID: Long,
        pMsg: Array<J2534Message>,
        pNumMsgs: IntArray,
        Timeout: Long
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruStartPeriodicMsg(
        ChannelID: Long,
        pMsg: J2534Message,
        pMsgID: LongArray,
        TimeInterval: Long
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruStopPeriodicMsg(ChannelID: Long, MsgID: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruStartMsgFilter(
        ChannelID: Long,
        FilterType: Long,
        pMask: J2534Message,
        pPattern: J2534Message,
        pFlowControlData: J2534Message?,
        pFilterID: LongArray
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruStopMsgFilter(ChannelID: Long, FilterID: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruSetProgrammingVoltage(DeviceID: Long, PinNumber: Long, Voltage: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruReadVersion(
        DeviceID: Long,
        pApiVersion: StringBuilder,
        pDllVersion: StringBuilder,
        pDevName: StringBuilder
    ): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruGetLastError(pErrorDescription: StringBuilder): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
    
    override fun passThruIoctl(Handle: Long, IoctlID: Long, pInput: Long, pOutput: Long): Long {
        // Implementation will be in the JNI layer
        return J2534Errors.ERR_NOT_SUPPORTED
    }
}