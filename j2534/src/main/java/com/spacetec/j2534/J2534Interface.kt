/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */

package com.spacetec.j2534

import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.j2534.constants.J2534Constants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * JNI interface to the J2534 Pass-Thru API.
 *
 * This class provides a Kotlin wrapper around the native J2534 API calls,
 * handling device driver loading, resource management, and error handling.
 */
class J2534Interface {
    
    private val mutex = Mutex()
    private val openDevices = ConcurrentHashMap<Int, String>()
    private val openChannels = ConcurrentHashMap<Int, ChannelInfo>()
    private val loadedLibraries = ConcurrentHashMap<String, Long>()
    
    private var isInitialized = false
    private var nativeLibraryHandle: Long = 0L
    
    private data class ChannelInfo(
        val deviceId: Int,
        val protocolId: Int,
        val flags: Int,
        val baudRate: Int,
        val openedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Initializes the J2534 interface and loads the native library.
     */
    suspend fun initialize(libraryPath: String? = null): Result<Unit> = mutex.withLock {
        if (isInitialized) {
            return Result.Success(Unit)
        }
        
        return try {
            val libPath = libraryPath ?: findDefaultLibrary()
            nativeLibraryHandle = nativeLoadLibrary(libPath)
            
            if (nativeLibraryHandle == 0L) {
                Result.Error(ConnectionException("Failed to load J2534 library: $libPath"))
            } else {
                loadedLibraries[libPath] = nativeLibraryHandle
                isInitialized = true
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(ConnectionException("Failed to initialize J2534 interface", e))
        }
    }
    
    /**
     * Opens a connection to a J2534 device.
     */
    suspend fun passThruOpen(name: String?, pDeviceId: Any? = null): Result<Int> {
        if (!isInitialized) {
            return Result.Error(ConnectionException("J2534 interface not initialized"))
        }
        
        return try {
            val deviceId = nativePassThruOpen(name)
            val errorCode = getLastError()
            
            if (errorCode == J2534Constants.STATUS_NOERROR) {
                openDevices[deviceId] = name ?: "Default Device"
                Result.Success(deviceId)
            } else {
                Result.Error(ConnectionException(
                    "PassThruOpen failed: ${J2534Constants.errorCodeToString(errorCode)}"
                ))
            }
        } catch (e: Exception) {
            Result.Error(ConnectionException("PassThruOpen failed", e))
        }
    }
    
    /**
     * Closes a connection to a J2534 device.
     */
    suspend fun passThruClose(deviceId: Int): Result<Unit> {
        if (!isInitialized) {
            return Result.Error(ConnectionException("J2534 interface not initialized"))
        }
        
        return try {
            val channelsToClose = openChannels.filterValues { it.deviceId == deviceId }.keys
            channelsToClose.forEach { channelId ->
                passThruDisconnect(channelId)
            }
            
            val errorCode = nativePassThruClose(deviceId)
            
            if (errorCode == J2534Constants.STATUS_NOERROR) {
                openDevices.remove(deviceId)
                Result.Success(Unit)
            } else {
                Result.Error(ConnectionException(
                    "PassThruClose failed: ${J2534Constants.errorCodeToString(errorCode)}"
                ))
            }
        } catch (e: Exception) {
            Result.Error(ConnectionException("PassThruClose failed", e))
        }
    }
    
    /**
     * Establishes a logical communication channel.
     */
    suspend fun passThruConnect(
        deviceId: Int,
        protocolId: Int,
        flags: Int,
        baudRate: Int
    ): Result<Int> {
        if (!isInitialized) {
            return Result.Error(ConnectionException("J2534 interface not initialized"))
        }
        
        if (!openDevices.containsKey(deviceId)) {
            return Result.Error(ConnectionException("Device $deviceId is not open"))
        }
        
        return try {
            val channelId = nativePassThruConnect(deviceId, protocolId, flags, baudRate)
            val errorCode = getLastError()
            
            if (errorCode == J2534Constants.STATUS_NOERROR) {
                openChannels[channelId] = ChannelInfo(deviceId, protocolId, flags, baudRate)
                Result.Success(channelId)
            } else {
                Result.Error(ConnectionException(
                    "PassThruConnect failed: ${J2534Constants.errorCodeToString(errorCode)}"
                ))
            }
        } catch (e: Exception) {
            Result.Error(ConnectionException("PassThruConnect failed", e))
        }
    }
    
    /**
     * Closes a logical communication channel.
     */
    suspend fun passThruDisconnect(channelId: Int): Result<Unit> {
        if (!isInitialized) {
            return Result.Error(ConnectionException("J2534 interface not initialized"))
        }
        
        return try {
            val errorCode = nativePassThruDisconnect(channelId)
            
            if (errorCode == J2534Constants.STATUS_NOERROR) {
                openChannels.remove(channelId)
                Result.Success(Unit)
            } else {
                Result.Error(ConnectionException(
                    "PassThruDisconnect failed: ${J2534Constants.errorCodeToString(errorCode)}"
                ))
            }
        } catch (e: Exception) {
            Result.Error(ConnectionException("PassThruDisconnect failed", e))
        }
    }
    
    private fun findDefaultLibrary(): String = "j2534.dll"
    private fun getLastError(): Int = if (isInitialized) nativeGetLastError() else J2534Constants.ERR_DEVICE_NOT_CONNECTED
    
    // Native method declarations
    private external fun nativeLoadLibrary(libraryPath: String): Long
    private external fun nativePassThruOpen(name: String?): Int
    private external fun nativePassThruClose(deviceId: Int): Int
    private external fun nativePassThruConnect(deviceId: Int, protocolId: Int, flags: Int, baudRate: Int): Int
    private external fun nativePassThruDisconnect(channelId: Int): Int
    private external fun nativeGetLastError(): Int
    
    companion object {
        init {
            try {
                System.loadLibrary("spacetec_j2534")
            } catch (e: UnsatisfiedLinkError) {
                // Library not found - will be handled during initialization
            }
        }
    }
}