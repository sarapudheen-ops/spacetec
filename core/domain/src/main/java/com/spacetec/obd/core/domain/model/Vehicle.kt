package com.spacetec.obd.core.domain.model

data class Vehicle(
    val vin: String,
    val make: String,
    val model: String,
    val year: Int,
    val ecuList: List<ECU> = emptyList()
)

data class ECU(
    val address: Int,
    val name: String,
    val type: ECUType,
    val supported: Boolean = true
)

enum class ECUType { ENGINE, TRANSMISSION, ABS, AIRBAG, BODY, HVAC, INSTRUMENT, OTHER }

interface VehicleProtocol {
    val make: String
    suspend fun detectVehicle(send: suspend (ByteArray) -> ByteArray?): Vehicle?
    suspend fun readDTCs(ecu: ECU, send: suspend (ByteArray) -> ByteArray?): List<DTC>
    suspend fun clearDTCs(ecu: ECU, send: suspend (ByteArray) -> ByteArray?): Boolean
}

class GenericVehicle : VehicleProtocol {
    override val make = "Generic"
    
    override suspend fun detectVehicle(send: suspend (ByteArray) -> ByteArray?): Vehicle? {
        val vinResp = send("0902".toByteArray()) ?: return null
        val vin = parseVIN(vinResp)
        return Vehicle(vin, decodeManufacturer(vin), "", decodeYear(vin))
    }
    
    override suspend fun readDTCs(ecu: ECU, send: suspend (ByteArray) -> ByteArray?): List<DTC> {
        val resp = send("03".toByteArray()) ?: return emptyList()
        return parseDTCResponse(resp)
    }
    
    override suspend fun clearDTCs(ecu: ECU, send: suspend (ByteArray) -> ByteArray?): Boolean {
        val resp = send("04".toByteArray()) ?: return false
        return resp.isNotEmpty()
    }
    
    private fun parseVIN(data: ByteArray): String {
        val str = String(data).replace(" ", "").replace("\r", "").replace(">", "")
        val idx = str.indexOf("49 02")
        return if (idx >= 0) str.substring(idx + 10).take(34).filter { it.isLetterOrDigit() } else "UNKNOWN"
    }
    
    private fun decodeManufacturer(vin: String) = when {
        vin.startsWith("1") || vin.startsWith("4") || vin.startsWith("5") -> "USA"
        vin.startsWith("W") -> "Germany"
        vin.startsWith("J") -> "Japan"
        vin.startsWith("K") -> "Korea"
        else -> "Unknown"
    }
    
    private fun decodeYear(vin: String): Int {
        if (vin.length < 10) return 0
        val yearCodes = "ABCDEFGHJKLMNPRSTVWXY123456789"
        val idx = yearCodes.indexOf(vin[9])
        return if (idx >= 0) 2010 + idx else 0
    }
    
    private fun parseDTCResponse(data: ByteArray): List<DTC> {
        val str = String(data).replace(" ", "").replace("\r", "")
        val dtcs = mutableListOf<DTC>()
        var i = 0
        while (i + 4 <= str.length) {
            val chunk = str.substring(i, i + 4)
            if (chunk.all { it.isDigit() || it in 'A'..'F' }) {
                val first = chunk[0].digitToInt(16)
                val code = "${"PCBU"[first shr 2]}${first and 0x03}${chunk.substring(1)}"
                if (code != "P0000") dtcs.add(DTC(code, "Generic DTC"))
            }
            i += 4
        }
        return dtcs
    }
}

object VehicleRegistry {
    private val protocols = mutableMapOf<String, VehicleProtocol>()
    init { protocols["Generic"] = GenericVehicle() }
    fun register(protocol: VehicleProtocol) { protocols[protocol.make] = protocol }
    fun get(make: String) = protocols[make] ?: protocols["Generic"] ?: GenericVehicle()
}