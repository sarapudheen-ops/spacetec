package com.spacetec.data.datasource.device

import com.spacetec.obd.core.domain.models.scanner.ScannerConnection
import com.spacetec.domain.models.vehicle.Vehicle
import com.spacetec.core.common.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * OBD Data Source implementation for communicating with vehicle diagnostic systems.
 *
 * Handles low-level communication with OBD-II adapters and translates raw data
 * to domain models for higher layers.
 */
class OBDDataSource(
    private val scannerConnection: ScannerConnection
) {
    /**
     * Initialize communication with the vehicle
     */
    suspend fun initialize(): Result<Boolean> {
        return try {
            // Reset ELM327
            val resetResult = sendCommand("ATZ")
            if (!resetResult.isSuccess) {
                return Result.Failure(Exception("Failed to reset ELM327"))
            }

            // Set protocol to auto
            val protocolResult = sendCommand("ATSP0")
            if (!protocolResult.isSuccess) {
                return Result.Failure(Exception("Failed to set protocol"))
            }

            Result.Success(true)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Detect supported PIDs for the connected vehicle
     */
    suspend fun detectSupportedPids(): Result<List<Int>> {
        return try {
            val result = sendCommand("0100")
            if (result.isSuccess && result.data != null) {
                parseSupportedPids(result.data)
            } else {
                Result.Failure(Exception("Failed to detect supported PIDs"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Read vehicle identification number (VIN)
     */
    suspend fun readVIN(): Result<String> {
        return try {
            // Use PID 02 to read VIN (UDS service 0x22 F1 90)
            val result = sendCommand("22F190")
            if (result.isSuccess && result.data != null) {
                Result.Success(parseVIN(result.data))
            } else {
                Result.Failure(Exception("Failed to read VIN"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Read diagnostic trouble codes
     */
    suspend fun readDTCs(): Result<List<String>> {
        return try {
            // Read DTCs using service 0x19 0x02 (UDS Read DTC Information)
            val result = sendCommand("1902FF")
            if (result.isSuccess && result.data != null) {
                Result.Success(parseDTCs(result.data))
            } else {
                Result.Failure(Exception("Failed to read DTCs"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Clear diagnostic trouble codes
     */
    suspend fun clearDTCs(): Result<Boolean> {
        return try {
            // Clear DTCs using service 0x14 (UDS Clear Diagnostic Information)
            val result = sendCommand("14FFFF")
            Result.Success(result.isSuccess)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Read live data for specific PID
     */
    suspend fun readLiveData(pid: Int): Result<String> {
        return try {
            val command = String.format("01%02X", pid)
            val result = sendCommand(command)
            if (result.isSuccess && result.data != null) {
                Result.Success(result.data)
            } else {
                Result.Failure(Exception("Failed to read live data for PID: $pid"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun sendCommand(command: String): Result<String> {
        return try {
            val response = scannerConnection.send(command.toByteArray() + "\r".toByteArray())
            if (response != null) {
                Result.Success(String(response).trim())
            } else {
                Result.Failure(Exception("No response from scanner"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun parseSupportedPids(response: String): Result<List<Int>> {
        // Parse response like "41 00 BE 3F B8 13"
        val parts = response.split(" ")
        if (parts.size < 4) {
            return Result.Failure(Exception("Invalid PID response"))
        }

        val supportedPids = mutableListOf<Int>()
        // Parse PID 1-32 (from response data)
        try {
            val dataBytes = parts.drop(1).map { it.toInt(16) }
            for ((index, byteValue) in dataBytes.withIndex()) {
                for (bit in 0..7) {
                    if ((byteValue and (1 shl (7 - bit))) != 0) {
                        supportedPids.add(index * 8 + bit + 1)
                    }
                }
            }
        } catch (e: NumberFormatException) {
            return Result.Failure(e)
        }

        return Result.Success(supportedPids)
    }

    private fun parseVIN(response: String): String {
        // Parse VIN from UDS response
        val cleanResponse = response.replace(" ", "").substringAfter("62F190")
        val vinBytes = cleanResponse.chunked(2).map { it.toInt(16).toByte() }
        return vinBytes.drop(1).joinToString("") { 
            if (it > 0) it.toInt().toChar().toString() else "" 
        }.trim()
    }

    private fun parseDTCs(response: String): List<String> {
        // Parse DTCs from UDS response
        val cleanResponse = response.replace(" ", "").substringAfter("5902")
        val dtcs = mutableListOf<String>()
        
        var i = 0
        while (i + 3 < cleanResponse.length) {
            val dtcBytes = cleanResponse.substring(i, i + 4)
            if (dtcBytes.length == 4) {
                val dtc = parseSingleDTC(dtcBytes)
                if (dtc.isNotEmpty()) dtcs.add(dtc)
            }
            i += 4
        }
        
        return dtcs
    }

    private fun parseSingleDTC(dtcBytes: String): String {
        if (dtcBytes.length < 4) return ""
        
        val firstByte = dtcBytes.substring(0, 2).toInt(16)
        val secondByte = dtcBytes.substring(2, 4).toInt(16)
        
        val type = when ((firstByte and 0xC0) shr 6) {
            0 -> 'P' // Powertrain
            1 -> 'C' // Chassis
            2 -> 'B' // Body
            3 -> 'U' // Network
            else -> '?'
        }
        
        val code = String.format("%c%02X%02X", type, firstByte and 0x3F, secondByte)
        return code
    }
}