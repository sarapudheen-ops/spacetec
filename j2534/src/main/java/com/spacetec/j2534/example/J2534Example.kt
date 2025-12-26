package com.spacetec.j2534.example

import com.spacetec.j2534.*
import com.spacetec.j2534.config.J2534Config
import com.spacetec.j2534.config.ProgrammingConfig
import com.spacetec.j2534.config.TimingConfig
import com.spacetec.j2534.message.MessageUtils
import com.spacetec.j2534.message.UdsUtils
import kotlinx.coroutines.*

/**
 * Example implementation showing how to use the J2534 API
 */
class J2534Example {
    private val manager = J2534Manager.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Complete example of connecting to a device and performing UDS communication
     */
    suspend fun runExample() {
        try {
            // Initialize the J2534 manager
            if (!manager.initialize()) {
                println("Failed to initialize J2534 manager")
                return
            }

            // Scan for available devices
            val devices = manager.scanForDevices()
            if (devices.isEmpty()) {
                println("No J2534 devices found")
                return
            }

            println("Found ${devices.size} J2534 device(s)")
            devices.forEachIndexed { index, device ->
                println("Device $index: ${device.name} (Vendor: ${device.vendor})")
            }

            // Connect to the first device using ISO-TP protocol
            val device = devices[0]
            val channelHandle = manager.connect(
                device,
                J2534Protocols.ISO15765,
                0L, // No special flags
                500000L // 500 kbps baudrate
            )

            if (channelHandle == J2534Errors.STATUS_NOERROR) {
                println("Failed to connect to device")
                return
            }

            println("Connected to device with channel handle: $channelHandle")

            // Create a channel object for easier management
            val channel = J2534Channel(channelHandle, manager, J2534Device(), J2534Protocols.ISO15765)

            // Create a communication session
            val session = J2534Session.createSession(channel, J2534Protocols.ISO15765)
            val sessionResult = session.start()

            if (sessionResult != J2534Errors.STATUS_NOERROR) {
                println("Failed to start session: $sessionResult")
                return
            }

            println("Session started successfully")

            // Example: Send a tester present message to keep the connection alive
            val testerPresentResponse = session.sendDiagnosticRequest(0x3EL.toByte(), byteArrayOf(0x00))
            if (testerPresentResponse != null) {
                println("Tester present response: ${testerPresentResponse.data.joinToString(", ") { String.format("0x%02X", it) }}")
            } else {
                println("No response to tester present message")
            }

            // Example: Read ECU data using UDS
            val ecuData = session.readEcuData(0xF190.toShort()) // VIN data identifier
            if (ecuData != null) {
                val vin = ecuData.joinToString("") { it.toChar().toString() }.trim { it <= ' ' }
                println("VIN: $vin")
            } else {
                println("Failed to read VIN data")
            }

            // Example: Perform ECU programming (simplified)
            val programmingData = ByteArray(1024) { (it % 256).toByte() } // Dummy programming data
            val programmingSuccess = session.performEcuProgramming(programmingData)
            if (programmingSuccess) {
                println("ECU programming completed successfully")
            } else {
                println("ECU programming failed")
            }

            // Clean up
            session.stop()
            channel.close()

            println("Example completed successfully")

        } catch (e: Exception) {
            println("Error during J2534 example: ${e.message}")
            e.printStackTrace()
        } finally {
            manager.cleanup()
        }
    }

    /**
     * Example of sending custom CAN messages
     */
    fun sendCustomCanMessage() {
        try {
            if (!manager.initialize()) {
                println("Failed to initialize J2534 manager")
                return
            }

            val devices = manager.scanForDevices()
            if (devices.isEmpty()) {
                println("No J2534 devices found")
                return
            }

            val device = devices[0]
            val channelHandle = manager.connect(
                device,
                J2534Protocols.CAN,
                0L, // No special flags
                500000L // 500 kbps baudrate
            )

            if (channelHandle == J2534Errors.STATUS_NOERROR) {
                println("Failed to connect to device")
                return
            }

            // Create a channel object
            val channel = J2534Channel(channelHandle, manager, J2534Device(), J2534Protocols.CAN)

            // Send a custom CAN message
            val canId = 0x7E0L // ECU physical address
            val data = byteArrayOf(0x02, 0x10.toByte(), 0x03.toByte()) // Diagnostic session control
            val result = channel.sendCanMessage(canId, data)

            if (result == J2534Errors.STATUS_NOERROR) {
                println("CAN message sent successfully")
            } else {
                println("Failed to send CAN message: $result")
            }

            // Clean up
            channel.close()
            manager.cleanup()

        } catch (e: Exception) {
            println("Error during CAN example: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Example of using message filters
     */
    fun useMessageFilters() {
        try {
            if (!manager.initialize()) {
                println("Failed to initialize J2534 manager")
                return
            }

            val devices = manager.scanForDevices()
            if (devices.isEmpty()) {
                println("No J2534 devices found")
                return
            }

            val device = devices[0]
            val channelHandle = manager.connect(
                device,
                J2534Protocols.ISO15765,
                0L, // No special flags
                500000L // 500 kbps baudrate
            )

            if (channelHandle == J2534Errors.STATUS_NOERROR) {
                println("Failed to connect to device")
                return
            }

            // Create a channel object
            val channel = J2534Channel(channelHandle, manager, J2534Device(), J2534Protocols.ISO15765)

            // Create a mask and pattern for filtering messages
            val mask = J2534Message().apply {
                protocolID = J2534Protocols.ISO15765
                data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) // Mask for first 5 bytes
            }

            val pattern = J2534Message().apply {
                protocolID = J2534Protocols.ISO15765
                data = byteArrayOf(0x00, 0x00, 0x07.toByte(), 0xE0.toByte(), 0x00) // Pattern matching ECU address 0x7E0
            }

            // Start a pass filter
            val filterId = channel.startMessageFilter(J2534FilterTypes.PASS_FILTER, mask, pattern, null)

            if (filterId != J2534Errors.STATUS_NOERROR) {
                println("Successfully created filter with ID: $filterId")
                
                // Now read messages - only matching messages will be received
                val messages = Array(10) { J2534Message() }
                val numMessages = intArrayOf(10)
                val readResult = manager.readMessages(channelHandle, messages, numMessages[0], 2000L)
                
                if (readResult == J2534Errors.STATUS_NOERROR) {
                    println("Read ${numMessages[0]} filtered messages")
                    for (i in 0 until numMessages[0]) {
                        println("Message $i: ${messages[i].data.joinToString(", ") { String.format("0x%02X", it) }}")
                    }
                }
                
                // Stop the filter
                channel.stopMessageFilter(filterId)
            } else {
                println("Failed to create filter")
            }

            // Clean up
            channel.close()
            manager.cleanup()

        } catch (e: Exception) {
            println("Error during filter example: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Example of setting programming voltage
     */
    fun setProgrammingVoltage() {
        try {
            if (!manager.initialize()) {
                println("Failed to initialize J2534 manager")
                return
            }

            val devices = manager.scanForDevices()
            if (devices.isEmpty()) {
                println("No J2534 devices found")
                return
            }

            val device = devices[0]
            // For programming voltage, we use the device handle directly (not channel handle)
            val deviceHandle = device.handle // This would be the actual device handle in a real implementation

            // Set 12V programming voltage on pin 15 (K-line programming)
            val result = manager.setProgrammingVoltage(deviceHandle, 15L, 12000L) // 12000 mV = 12V

            if (result == J2534Errors.STATUS_NOERROR) {
                println("Programming voltage set successfully")
            } else {
                println("Failed to set programming voltage: $result")
            }

            // Turn off programming voltage
            val offResult = manager.setProgrammingVoltage(deviceHandle, 15L, 0L) // 0V = off

            if (offResult == J2534Errors.STATUS_NOERROR) {
                println("Programming voltage turned off successfully")
            } else {
                println("Failed to turn off programming voltage: $offResult")
            }

            manager.cleanup()

        } catch (e: Exception) {
            println("Error during programming voltage example: ${e.message}")
            e.printStackTrace()
        }
    }
}