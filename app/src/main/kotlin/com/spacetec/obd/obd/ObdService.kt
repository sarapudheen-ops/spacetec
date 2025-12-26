package com.spacetec.obd.obd

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ObdService(context: Context) {
    
    val connection = ObdConnection(context)
    val vwManager by lazy { VwProtocolManager(connection) }

    val currentProtocol by lazy { connection.currentProtocol }

    private val _liveData = MutableStateFlow<Map<String, LiveDataValue>>(emptyMap())
    val liveData: StateFlow<Map<String, LiveDataValue>> = _liveData
    
    private val _dtcList = MutableStateFlow<List<DtcDecoder.DtcInfo>>(emptyList())
    val dtcList: StateFlow<List<DtcDecoder.DtcInfo>> = _dtcList
    
    private val _vehicleInfo = MutableStateFlow<VehicleInfo?>(null)
    val vehicleInfo: StateFlow<VehicleInfo?> = _vehicleInfo
    
    private val _supportedPids = MutableStateFlow<List<Int>>(emptyList())
    val supportedPids: StateFlow<List<Int>> = _supportedPids
    
    private var liveDataJob: Job? = null
    
    data class LiveDataValue(
        val pid: Int,
        val name: String,
        val value: Double,
        val displayValue: String,
        val unit: String,
        val minValue: Double = 0.0,
        val maxValue: Double = 100.0
    )
    
    data class VehicleInfo(
        val vin: String?,
        val isVwGroup: Boolean,
        val protocol: String?,
        val ecuName: String?,
        val calibrationId: String?,
        val supportedPidCount: Int
    )
    
    /**
     * Initialize vehicle communication and get vehicle info.
     */
    suspend fun initializeVehicle(): VehicleInfo? {
        if (!connection.isConnected()) return null
        
        val vwResult = vwManager.initializeVwCommunication()
        
        val info = VehicleInfo(
            vin = vwResult.vin,
            isVwGroup = vwResult.isVwVehicle,
            protocol = vwResult.protocol,
            ecuName = vwResult.ecuInfo["ECU_NAME"],
            calibrationId = vwResult.ecuInfo["CALIBRATION_ID"],
            supportedPidCount = vwResult.supportedPids.size
        )
        
        _vehicleInfo.value = info
        _supportedPids.value = vwResult.supportedPids.mapNotNull { it.toIntOrNull(16) }
        
        return info
    }
    
    /**
     * Read all DTCs (stored, pending, permanent).
     */
    suspend fun readAllDtcs(): List<DtcDecoder.DtcInfo> {
        if (!connection.isConnected()) return emptyList()
        
        val allDtcs = mutableListOf<DtcDecoder.DtcInfo>()
        
        // Stored DTCs (Mode 03)
        val storedResponse = connection.sendCommand("03")
        DtcDecoder.parseDtcResponse(storedResponse).forEach { code ->
            allDtcs.add(DtcDecoder.getDtcInfo(code))
        }
        
        // Pending DTCs (Mode 07)
        val pendingResponse = connection.sendCommand("07")
        DtcDecoder.parseDtcResponse(pendingResponse).forEach { code ->
            val info = DtcDecoder.getDtcInfo(code)
            if (allDtcs.none { it.code == info.code }) {
                allDtcs.add(info)
            }
        }
        
        // Permanent DTCs (Mode 0A)
        val permanentResponse = connection.sendCommand("0A")
        DtcDecoder.parseDtcResponse(permanentResponse).forEach { code ->
            val info = DtcDecoder.getDtcInfo(code)
            if (allDtcs.none { it.code == info.code }) {
                allDtcs.add(info)
            }
        }
        
        _dtcList.value = allDtcs
        Log.d(TAG, "Found ${allDtcs.size} DTCs")
        return allDtcs
    }
    
    suspend fun readStoredDtcs(): List<DtcDecoder.DtcInfo> {
        if (!connection.isConnected()) return emptyList()
        val response = connection.sendCommand("03")
        val dtcs = DtcDecoder.parseDtcResponse(response).map { DtcDecoder.getDtcInfo(it) }
        _dtcList.value = dtcs
        return dtcs
    }
    
    suspend fun readPendingDtcs(): List<DtcDecoder.DtcInfo> {
        if (!connection.isConnected()) return emptyList()
        val response = connection.sendCommand("07")
        return DtcDecoder.parseDtcResponse(response).map { DtcDecoder.getDtcInfo(it) }
    }
    
    suspend fun readPermanentDtcs(): List<DtcDecoder.DtcInfo> {
        if (!connection.isConnected()) return emptyList()
        val response = connection.sendCommand("0A")
        return DtcDecoder.parseDtcResponse(response).map { DtcDecoder.getDtcInfo(it) }
    }
    
    suspend fun clearDtcs(): Boolean {
        if (!connection.isConnected()) return false
        val response = connection.sendCommand("04")
        val success = response.contains("44") || (!response.contains("ERROR") && !response.contains("NO DATA"))
        if (success) {
            _dtcList.value = emptyList()
        }
        return success
    }
    
    /**
     * Read a single PID value.
     */
    suspend fun readPid(pid: Int): PidDecoder.PidValue? {
        if (!connection.isConnected()) return null
        
        val command = String.format("01%02X", pid)
        val response = connection.sendCommand(command)
        
        // Parse response
        val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length < 4) return null
        
        // Find data after response header (41 XX)
        val expectedHeader = String.format("41%02X", pid).uppercase()
        val headerIndex = hex.uppercase().indexOf(expectedHeader)
        if (headerIndex < 0) return null
        
        val dataStart = headerIndex + 4
        val dataHex = hex.substring(dataStart)
        
        val bytes = dataHex.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        
        return PidDecoder.decode(pid, bytes)
    }
    
    /**
     * Start continuous live data monitoring.
     */
    fun startLiveData(scope: CoroutineScope, pids: List<Int>? = null) {
        liveDataJob?.cancel()
        
        // Use provided PIDs or default set
        val pidsToRead = pids ?: listOf(0x0C, 0x0D, 0x05, 0x04, 0x11, 0x2F, 0x0F, 0x10, 0x42, 0x46)
        
        liveDataJob = scope.launch {
            while (isActive && connection.isConnected()) {
                val data = mutableMapOf<String, LiveDataValue>()
                
                for (pid in pidsToRead) {
                    if (!isActive) break
                    
                    try {
                        readPid(pid)?.let { pidValue ->
                            val def = PidDecoder.PIDS[pid]
                            data[pidValue.name] = LiveDataValue(
                                pid = pid,
                                name = pidValue.name,
                                value = pidValue.value,
                                displayValue = formatValue(pidValue.value, pidValue.unit),
                                unit = pidValue.unit,
                                minValue = def?.minValue ?: 0.0,
                                maxValue = def?.maxValue ?: 100.0
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read PID ${String.format("%02X", pid)}: ${e.message}")
                    }
                }
                
                _liveData.value = data
                delay(200) // ~5 Hz update rate
            }
        }
    }
    
    fun stopLiveData() {
        liveDataJob?.cancel()
        liveDataJob = null
    }
    
    private fun formatValue(value: Double, unit: String): String {
        return when {
            unit == "rpm" -> "${value.toInt()} $unit"
            unit == "km/h" -> "${value.toInt()} $unit"
            unit.contains("°") -> "${value.toInt()}$unit"
            unit == "%" -> "${"%.1f".format(value)}$unit"
            unit == "V" -> "${"%.2f".format(value)} $unit"
            unit == "kPa" -> "${value.toInt()} $unit"
            unit == "g/s" -> "${"%.2f".format(value)} $unit"
            unit == "L/h" -> "${"%.2f".format(value)} $unit"
            else -> "${"%.1f".format(value)} $unit"
        }
    }
    
    /**
     * Get VIN using VW manager.
     */
    suspend fun getVin(): String? {
        if (!connection.isConnected()) return null
        return vwManager.getVin()
    }
    
    /**
     * Get freeze frame data for a specific DTC.
     */
    suspend fun getFreezeFrame(frameNumber: Int = 0): Map<String, LiveDataValue>? {
        if (!connection.isConnected()) return null
        
        val data = mutableMapOf<String, LiveDataValue>()
        val pids = listOf(0x02, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x11)
        
        for (pid in pids) {
            val command = String.format("02%02X%02X", pid, frameNumber)
            val response = connection.sendCommand(command)
            
            val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
            if (hex.length >= 6) {
                val dataStart = 6 // After 42 XX FF
                if (hex.length > dataStart) {
                    val bytes = hex.substring(dataStart).chunked(2)
                        .mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
                    
                    PidDecoder.decode(pid, bytes)?.let { pidValue ->
                        val def = PidDecoder.PIDS[pid]
                        data[pidValue.name] = LiveDataValue(
                            pid = pid,
                            name = pidValue.name,
                            value = pidValue.value,
                            displayValue = formatValue(pidValue.value, pidValue.unit),
                            unit = pidValue.unit,
                            minValue = def?.minValue ?: 0.0,
                            maxValue = def?.maxValue ?: 100.0
                        )
                    }
                }
            }
        }
        
        return data.ifEmpty { null }
    }
    
    /**
     * Get monitor status (readiness tests).
     */
    suspend fun getMonitorStatus(): MonitorStatus? {
        if (!connection.isConnected()) return null
        
        val response = connection.sendCommand("0101")
        val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
        
        if (hex.length < 12) return null
        
        // Parse bytes after 41 01
        val dataStart = hex.uppercase().indexOf("4101")
        if (dataStart < 0) return null
        
        val data = hex.substring(dataStart + 4)
        if (data.length < 8) return null
        
        val byteA = data.substring(0, 2).toIntOrNull(16) ?: return null
        val byteB = data.substring(2, 4).toIntOrNull(16) ?: return null
        val byteC = data.substring(4, 6).toIntOrNull(16) ?: return null
        val byteD = data.substring(6, 8).toIntOrNull(16) ?: return null
        
        return MonitorStatus(
            milOn = (byteA and 0x80) != 0,
            dtcCount = byteA and 0x7F,
            misfire = TestResult((byteB and 0x01) != 0, (byteB and 0x10) != 0),
            fuelSystem = TestResult((byteB and 0x02) != 0, (byteB and 0x20) != 0),
            components = TestResult((byteB and 0x04) != 0, (byteB and 0x40) != 0),
            catalyst = TestResult((byteC and 0x01) != 0, (byteD and 0x01) != 0),
            heatedCatalyst = TestResult((byteC and 0x02) != 0, (byteD and 0x02) != 0),
            evapSystem = TestResult((byteC and 0x04) != 0, (byteD and 0x04) != 0),
            secondaryAir = TestResult((byteC and 0x08) != 0, (byteD and 0x08) != 0),
            oxygenSensor = TestResult((byteC and 0x20) != 0, (byteD and 0x20) != 0),
            oxygenSensorHeater = TestResult((byteC and 0x40) != 0, (byteD and 0x40) != 0),
            egr = TestResult((byteC and 0x80) != 0, (byteD and 0x80) != 0)
        )
    }
    
    data class MonitorStatus(
        val milOn: Boolean,
        val dtcCount: Int,
        val misfire: TestResult,
        val fuelSystem: TestResult,
        val components: TestResult,
        val catalyst: TestResult,
        val heatedCatalyst: TestResult,
        val evapSystem: TestResult,
        val secondaryAir: TestResult,
        val oxygenSensor: TestResult,
        val oxygenSensorHeater: TestResult,
        val egr: TestResult
    )
    
    data class TestResult(val available: Boolean, val complete: Boolean)
    
    companion object {
        private const val TAG = "ObdService"
    }
}
