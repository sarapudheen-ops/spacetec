package com.spacetec.protocol.obd

import com.spacetec.protocol.obd.services.*
import com.spacetec.protocol.obd.pid.PIDRegistry
import com.spacetec.protocol.obd.dtc.DTCDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.*

/**
 * Main OBD-II Protocol Implementation
 * Implements SAE J1979 (ISO 15031-5) standards
 */
class OBDProtocol {
    private val serviceHandlers = mapOf(
        0x01 to Service01Handler(),
        0x02 to Service02Handler(),
        0x03 to Service03Handler(),
        0x04 to Service04Handler(),
        0x05 to Service05Handler(),
        0x06 to Service06Handler(),
        0x07 to Service07Handler(),
        0x08 to Service08Handler(),
        0x09 to Service09Handler(),
        0x0A to Service0AHandler()
    )

    /**
     * Read current data for specified PIDs
     * Implements Service 01 - Current Data
     * Supports batch processing of up to 6 PIDs per request
     */
    suspend fun readCurrentData(pids: List<Int>): Flow<OBDResult<List<PIDValue>>> = flow {
        try {
            // Validate PID count - max 6 per request according to standards
            if (pids.size > 6) {
                emit(OBDResult.Failure("Maximum 6 PIDs allowed per request"))
                return@flow
            }

            // Validate PIDs
            val invalidPids = pids.filter { !PIDRegistry.isValidPID(it) }
            if (invalidPids.isNotEmpty()) {
                emit(OBDResult.Failure("Invalid PIDs: ${invalidPids.joinToString(", ")}"))
                return@flow
            }

            // Get the service handler for current data
            val handler = serviceHandlers[0x01] as Service01Handler
            
            // Process the request
            val result = handler.handleRequest(pids)
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading current data: ${e.message}"))
        }
    }

    /**
     * Get supported PIDs for the vehicle
     * Uses PID 0x00 (0) and subsequent discovery PIDs to determine supported PIDs
     */
    suspend fun getSupportedPIDs(): Flow<OBDResult<Set<Int>>> = flow {
        try {
            val supportedPids = mutableSetOf<Int>()
            var pid = 0x00  // Start with PID 00
            
            // Loop through discovery PIDs (00, 20, 40, 60, 80, A0, C0, E0)
            while (pid <= 0xE0) {
                val result = readCurrentData(listOf(pid)).collectLatest { 
                    if (it is OBDResult.Success) {
                        val pidValues = it.data
                        if (pidValues.isNotEmpty()) {
                            val pidValue = pidValues.first()
                            val supportedBits = pidValue.value as? ByteArray
                            if (supportedBits != null && supportedBits.size >= 4) {
                                // Each bit represents a PID support status
                                for (i in 0 until 32) {  // 32 bits in 4 bytes
                                    val byteIndex = i / 8
                                    val bitIndex = i % 8
                                    val bitValue = (supportedBits[byteIndex].toInt() shr bitIndex) and 1
                                    if (bitValue == 1) {
                                        supportedPids.add(pid + i + 1)  // PIDs are 1-indexed
                                    }
                                }
                            }
                        }
                    }
                }
                pid += 0x20  // Move to next discovery PID
            }
            
            emit(OBDResult.Success(supportedPids))
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error getting supported PIDs: ${e.message}"))
        }
    }

    /**
     * Read freeze frame data for a specific DTC
     * Implements Service 02 - Freeze Frame
     */
    suspend fun readFreezeFrameData(dtcs: List<String>): Flow<OBDResult<Map<String, List<PIDValue>>>> = flow {
        try {
            val handler = serviceHandlers[0x02] as Service02Handler
            val result = handler.handleRequest(dtcs)
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading freeze frame data: ${e.message}"))
        }
    }

    /**
     * Read stored DTCs
     * Implements Service 03 - Stored DTCs
     */
    suspend fun readStoredDTCs(): Flow<OBDResult<List<DTC>>> = flow {
        try {
            val handler = serviceHandlers[0x03] as Service03Handler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading stored DTCs: ${e.message}"))
        }
    }

    /**
     * Clear DTCs
     * Implements Service 04 - Clear DTCs
     */
    suspend fun clearDTCs(): Flow<OBDResult<Unit>> = flow {
        try {
            val handler = serviceHandlers[0x04] as Service04Handler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error clearing DTCs: ${e.message}"))
        }
    }

    /**
     * Read oxygen sensor data
     * Implements Service 05 - Oxygen Sensor
     */
    suspend fun readOxygenSensorData(): Flow<OBDResult<List<OxygenSensorData>>> = flow {
        try {
            val handler = serviceHandlers[0x05] as Service05Handler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading oxygen sensor data: ${e.message}"))
        }
    }

    /**
     * Read test results
     * Implements Service 06 - Test Results
     */
    suspend fun readTestResults(): Flow<OBDResult<List<TestResult>>> = flow {
        try {
            val handler = serviceHandlers[0x06] as Service06Handler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading test results: ${e.message}"))
        }
    }

    /**
     * Read pending DTCs
     * Implements Service 07 - Pending DTCs
     */
    suspend fun readPendingDTCs(): Flow<OBDResult<List<DTC>>> = flow {
        try {
            val handler = serviceHandlers[0x07] as Service07Handler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading pending DTCs: ${e.message}"))
        }
    }

    /**
     * Control operation
     * Implements Service 08 - Control Operation
     */
    suspend fun controlOperation(operation: Int, data: ByteArray): Flow<OBDResult<ByteArray>> = flow {
        try {
            val handler = serviceHandlers[0x08] as Service08Handler
            val result = handler.handleRequest(operation, data)
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error in control operation: ${e.message}"))
        }
    }

    /**
     * Read vehicle information
     * Implements Service 09 - Vehicle Information
     */
    suspend fun readVehicleInformation(pids: List<Int>): Flow<OBDResult<List<VehicleInfo>>> = flow {
        try {
            val handler = serviceHandlers[0x09] as Service09Handler
            val result = handler.handleRequest(pids)
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading vehicle information: ${e.message}"))
        }
    }

    /**
     * Read permanent DTCs
     * Implements Service 0A - Permanent DTCs
     */
    suspend fun readPermanentDTCs(): Flow<OBDResult<List<DTC>>> = flow {
        try {
            val handler = serviceHandlers[0x0A] as Service0AHandler
            val result = handler.handleRequest()
            emit(result)
        } catch (e: Exception) {
            emit(OBDResult.Failure("Error reading permanent DTCs: ${e.message}"))
        }
    }
}

/**
 * Result wrapper for OBD operations
 */
sealed class OBDResult<out T> {
    data class Success<T>(val data: T) : OBDResult<T>()
    data class Failure(val error: String) : OBDResult<Nothing>()
}

/**
 * Data class representing a PID value
 */
data class PIDValue(
    val pid: Int,
    val name: String,
    val value: Any,  // Could be Int, Float, String, ByteArray depending on the PID
    val unit: String,
    val rawResponse: ByteArray
)

/**
 * Data class representing a DTC
 */
data class DTC(
    val code: String,  // e.g., "P0123"
    val description: String,
    val status: DTCStatus,
    val severity: DTCSeverity
)

/**
 * DTC Status flags
 */
data class DTCStatus(
    val isPending: Boolean,
    val isStored: Boolean,
    val isTestFailed: Boolean,
    val isTestFailedThisCycle: Boolean,
    val isTestCompletedSinceLastClear: Boolean,
    val isTestFailedSinceLastClear: Boolean,
    val isConfirmed: Boolean,
    val isCurrentlyActive: Boolean
)

/**
 * DTC Severity
 */
enum class DTCSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Oxygen Sensor Data
 */
data class OxygenSensorData(
    val sensorId: Int,
    val voltage: Float,
    val shortTermFuelTrim: Float,
    val longTermFuelTrim: Float
)

/**
 * Test Result
 */
data class TestResult(
    val testId: Int,
    val result: ByteArray
)

/**
 * Vehicle Information
 */
data class VehicleInfo(
    val pid: Int,
    val value: String
)

// Extension function to collect the latest value from a flow
suspend fun <T> Flow<T>.collectLatest(action: (T) -> Unit) {
    collect { value ->
        action(value)
    }
}