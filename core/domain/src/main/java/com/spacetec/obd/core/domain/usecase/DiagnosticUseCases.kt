package com.spacetec.obd.core.domain.usecase

import com.spacetec.obd.core.domain.model.DTC
import com.spacetec.obd.core.domain.model.Vehicle
import com.spacetec.obd.core.domain.model.VehicleRegistry
import com.spacetec.obd.core.domain.scanner.Scanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// DTC Use Cases
class ReadDTCsUseCase(private val scanner: Scanner) {
    suspend operator fun invoke(): List<DTC> {
        val response = scanner.send("03".toByteArray()) ?: return emptyList()
        return DtcParsers.parseDtcResponse(
            response = response,
            expectedHeader = "43",
            description = "Stored DTC"
        )
    }
}

class ClearDTCsUseCase(private val scanner: Scanner) {
    suspend operator fun invoke(): Boolean {
        val response = scanner.send("04".toByteArray()) ?: return false
        // 0x44 is the response header for OBD service 04
        return String(response).replace(" ", "").contains("44")
    }
}

class ReadPendingDTCsUseCase(private val scanner: Scanner) {
    suspend operator fun invoke(): List<DTC> {
        val response = scanner.send("07".toByteArray()) ?: return emptyList()
        return DtcParsers.parseDtcResponse(
            response = response,
            expectedHeader = "47",
            description = "Pending DTC"
        )
    }
}

private object DtcParsers {
    /**
     * Parses a hex-encoded OBD DTC response.
     *
     * For service 03, ECU typically responds with header 0x43.
     * For service 07, ECU typically responds with header 0x47.
     */
    fun parseDtcResponse(
        response: ByteArray,
        expectedHeader: String,
        description: String
    ): List<DTC> {
        val str = String(response)
            .replace(" ", "")
            .replace("\r", "")
            .replace(">", "")
            .uppercase()

        val headerIndex = str.indexOf(expectedHeader)
        if (headerIndex < 0) return emptyList()

        // Skip the 1-byte response header (2 hex chars)
        var i = headerIndex + expectedHeader.length
        val dtcs = mutableListOf<DTC>()

        while (i + 4 <= str.length) {
            val b1 = str.substring(i, i + 2).toIntOrNull(16) ?: break
            val b2 = str.substring(i + 2, i + 4).toIntOrNull(16) ?: break

            // P/C/B/U (2 MSBs), then 2 bits for digit, then 4 bits for digit, then next byte.
            val system = "PCBU"[(b1 shr 6) and 0x03]
            val digit1 = (b1 shr 4) and 0x03
            val digit2 = b1 and 0x0F
            val digit34 = b2.toString(16).padStart(2, '0').uppercase()

            val code = "$system$digit1${digit2.toString(16).uppercase()}$digit34"
            if (code != "P0000") {
                dtcs.add(DTC(code = code, description = description))
            }
            i += 4
        }
        return dtcs
    }
}

// Live Data Use Cases
class ReadLiveDataUseCase(private val scanner: Scanner) {
    suspend fun readPID(pid: Int): Float? {
        val cmd = "01${pid.toString(16).padStart(2, '0').uppercase()}"
        val response = scanner.send(cmd.toByteArray()) ?: return null
        return parsePIDResponse(pid, response)
    }
    
    fun streamPIDs(pids: List<Int>, intervalMs: Long = 500): Flow<Map<Int, Float>> = flow {
        while (true) {
            val values = pids.mapNotNull { pid -> readPID(pid)?.let { pid to it } }.toMap()
            emit(values)
            delay(intervalMs)
        }
    }
    
    private fun parsePIDResponse(pid: Int, data: ByteArray): Float? {
        val str = String(data).replace(" ", "").replace("\r", "")
        val prefix = "41${pid.toString(16).padStart(2, '0').uppercase()}"
        val idx = str.indexOf(prefix)
        if (idx < 0) return null
        val valueStr = str.substring(idx + prefix.length).take(4)
        val bytes = valueStr.chunked(2).mapNotNull { it.toIntOrNull(16) }
        return when (pid) {
            0x04 -> bytes.getOrNull(0)?.div(2.55f)
            0x05 -> bytes.getOrNull(0)?.minus(40)?.toFloat()
            0x0C -> bytes.getOrNull(0)?.times(256)?.plus(bytes.getOrNull(1) ?: 0)?.div(4f)
            0x0D -> bytes.getOrNull(0)?.toFloat()
            0x0F -> bytes.getOrNull(0)?.minus(40)?.toFloat()
            0x11 -> bytes.getOrNull(0)?.div(2.55f)
            else -> bytes.getOrNull(0)?.toFloat()
        }
    }
}

// Vehicle Use Cases
class DetectVehicleUseCase(private val scanner: Scanner) {
    suspend operator fun invoke(): Vehicle? {
        val protocol = VehicleRegistry.get("Generic")
        return protocol.detectVehicle { scanner.send(it) }
    }
}
