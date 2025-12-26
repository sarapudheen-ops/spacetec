/**
 * ScannerConnection.kt
 *
 * Domain interface for scanner connections used by protocols.
 * This interface defines the contract that all scanner connection implementations must follow.
 * It provides basic communication operations needed by protocol handlers.
 */

package com.spacetec.core.domain.scanner

/**
 * Interface for scanner connections.
 * Defines the basic communication contract that scanner implementations must provide.
 * Protocol modules use this interface to communicate with scanners without knowing
 * the specific transport mechanism (Bluetooth, WiFi, USB, etc.).
 */
interface ScannerConnection {

    /**
     * Whether the connection is currently active and ready for communication.
     */
    val isConnected: Boolean

    /**
     * Write data to the scanner.
     * This method sends raw bytes to the connected scanner device.
     *
     * @param data The byte array to send
     * @throws Exception if the write operation fails
     */
    suspend fun write(data: ByteArray)

    /**
     * Read data from the scanner with a timeout.
     * This method reads available data from the scanner, waiting up to the specified timeout.
     *
     * @param timeoutMs Maximum time to wait for data in milliseconds (default: 1000ms)
     * @return ByteArray containing the received data
     * @throws Exception if the read operation fails or times out
     */
    suspend fun read(timeoutMs: Long = 1000L): ByteArray

    /**
     * Close the connection.
     * This method gracefully closes the connection and releases any associated resources.
     */
    suspend fun close()
}