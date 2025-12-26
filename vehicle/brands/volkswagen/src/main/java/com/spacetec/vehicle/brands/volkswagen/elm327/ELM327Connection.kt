/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 */
package com.spacetec.vehicle.brands.volkswagen.elm327

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles low-level Bluetooth socket connection to ELM327 adapter
 * Implements multiple connection strategies for maximum compatibility
 */
class ELM327Connection {

    private var device: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    var callback: ConnectionCallback? = null
    var connectionTimeout = 15000
    var maxRetries = 3

    interface ConnectionCallback {
        fun onConnecting(status: String)
        fun onConnected()
        fun onDisconnected()
        fun onConnectionFailed(reason: String, e: Exception?)
    }

    fun connect(device: BluetoothDevice) {
        if (isConnecting.get()) {
            Log.w(TAG, "Connection already in progress")
            return
        }

        if (isConnected.get()) disconnect()

        this.device = device
        isConnecting.set(true)

        executor.execute {
            try {
                var connected = false
                var lastException: Exception? = null

                for (attempt in 1..maxRetries) {
                    if (connected) break

                    Log.d(TAG, "Connection attempt $attempt/$maxRetries")
                    notifyStatus("Connection attempt $attempt/$maxRetries")

                    try {
                        connected = tryConnect()
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "Attempt $attempt failed: ${e.message}")
                        if (attempt < maxRetries) Thread.sleep(1000)
                    }
                }

                if (connected) {
                    isConnected.set(true)
                    callback?.onConnected()
                } else {
                    callback?.onConnectionFailed("Failed after $maxRetries attempts", lastException)
                }
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun tryConnect(): Boolean {
        closeSocket()

        // Strategy 1: Standard secure RFCOMM
        notifyStatus("Trying standard secure connection...")
        runCatching { if (tryStandardSecureConnection()) return true }

        // Strategy 2: Insecure RFCOMM
        notifyStatus("Trying insecure connection...")
        runCatching { if (tryInsecureConnection()) return true }

        // Strategy 3: Reflection-based connection
        notifyStatus("Trying reflection-based connection...")
        runCatching { if (tryReflectionConnection()) return true }

        // Strategy 4: Alternative UUIDs
        ALTERNATIVE_UUIDS.forEach { uuid ->
            notifyStatus("Trying alternative UUID...")
            runCatching { if (tryConnectionWithUUID(uuid)) return true }
        }

        return false
    }

    private fun tryStandardSecureConnection(): Boolean {
        socket = device?.createRfcommSocketToServiceRecord(SPP_UUID)
        return performConnection()
    }

    private fun tryInsecureConnection(): Boolean {
        socket = device?.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        return performConnection()
    }

    private fun tryReflectionConnection(): Boolean {
        val channels = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        for (channel in channels) {
            runCatching {
                val method = device?.javaClass?.getMethod("createRfcommSocket", Int::class.java)
                socket = method?.invoke(device, channel) as? BluetoothSocket
                if (performConnection()) {
                    Log.d(TAG, "Reflection connection succeeded on channel $channel")
                    return true
                }
            }
            closeSocket()
        }

        // Try insecure reflection
        runCatching {
            val method = device?.javaClass?.getMethod("createInsecureRfcommSocket", Int::class.java)
            socket = method?.invoke(device, 1) as? BluetoothSocket
            if (performConnection()) {
                Log.d(TAG, "Insecure reflection connection succeeded")
                return true
            }
        }
        closeSocket()

        return false
    }

    private fun tryConnectionWithUUID(uuid: UUID): Boolean {
        socket = device?.createRfcommSocketToServiceRecord(uuid)
        return performConnection()
    }

    private fun performConnection(): Boolean {
        val sock = socket ?: return false

        val future = executor.submit<Boolean> {
            try {
                sock.connect()
                inputStream = sock.inputStream
                outputStream = sock.outputStream
                true
            } catch (e: IOException) {
                Log.d(TAG, "Socket connect failed: ${e.message}")
                false
            }
        }

        return try {
            future.get(connectionTimeout.toLong(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            Log.d(TAG, "Connection timed out")
            closeSocket()
            false
        } catch (e: Exception) {
            Log.d(TAG, "Connection exception: ${e.message}")
            closeSocket()
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        isConnected.set(false)
        closeSocket()
        callback?.onDisconnected()
    }

    private fun closeSocket() {
        runCatching { inputStream?.close() }
        inputStream = null
        runCatching { outputStream?.close() }
        outputStream = null
        runCatching { socket?.close() }
        socket = null
    }

    @Synchronized
    @Throws(IOException::class)
    fun sendRaw(data: ByteArray) {
        if (!isConnected.get() || outputStream == null) {
            throw IOException("Not connected")
        }
        outputStream?.write(data)
        outputStream?.flush()
    }

    @Synchronized
    @Throws(IOException::class)
    fun sendCommand(command: String) {
        val cmd = if (command.endsWith("\r")) command else "$command\r"
        Log.d(TAG, "TX: ${cmd.trim()}")
        sendRaw(cmd.toByteArray())
    }

    @Throws(IOException::class)
    fun readResponse(timeoutMs: Int): String {
        if (!isConnected.get() || inputStream == null) {
            throw IOException("Not connected")
        }

        val response = StringBuilder()
        val startTime = System.currentTimeMillis()
        val buffer = ByteArray(256)

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val available = inputStream?.available() ?: 0
            if (available > 0) {
                val bytesRead = inputStream?.read(buffer) ?: 0
                if (bytesRead > 0) {
                    val chunk = String(buffer, 0, bytesRead)
                    response.append(chunk)
                    if (chunk.contains(">")) break
                }
            } else {
                Thread.sleep(10)
            }
        }

        val result = response.toString()
        Log.d(TAG, "RX: ${result.replace("\r", "\\r").replace("\n", "\\n")}")
        return result
    }

    @Throws(IOException::class)
    fun sendAndReceive(command: String, timeoutMs: Int): String {
        clearInputBuffer()
        sendCommand(command)
        return readResponse(timeoutMs)
    }

    @Throws(IOException::class)
    fun clearInputBuffer() {
        inputStream?.let { stream ->
            while (stream.available() > 0) {
                stream.read()
            }
        }
    }

    fun isConnected(): Boolean = isConnected.get() && socket?.isConnected == true

    fun getDevice(): BluetoothDevice? = device

    private fun notifyStatus(status: String) {
        callback?.onConnecting(status)
    }

    fun cleanup() {
        disconnect()
        executor.shutdown()
    }

    companion object {
        private const val TAG = "ELM327Connection"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val ALTERNATIVE_UUIDS = arrayOf(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"),
            UUID.fromString("0000112F-0000-1000-8000-00805F9B34FB")
        )
    }
}
