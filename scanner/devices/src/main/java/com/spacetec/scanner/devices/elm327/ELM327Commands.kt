package com.spacetec.scanner.devices.elm327

/**
 * ELM327 Command definitions and utilities for OBD-II communication.
 *
 * This class provides standardized commands for communicating with ELM327-based
 * OBD-II adapters. It includes both basic ELM327 AT commands and OBD-II PIDs.
 */
object ELM327Commands {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ELM327 AT COMMANDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Reset all to defaults
     */
    const val RESET = "ATZ"
    
    /**
     * Reset the OBD interface
     */
    const val RESET_INTERFACE = "ATWS"
    
    /**
     * Toggle adaptive timing
     */
    const val ADAPTIVE_TIMING = "ATAT"
    
    /**
     * Set protocol to auto
     */
    const val SET_PROTOCOL_AUTO = "ATSP0"
    
    /**
     * Set protocol to specific type (0-6, A-F)
     */
    fun setProtocol(protocol: String) = "ATSP$protocol"
    
    /**
     * Set protocol to auto, find best match
     */
    const val SET_PROTOCOL_AUTO_BEST = "ATSP1"
    
    /**
     * Describe current protocol
     */
    const val DESCRIBE_PROTOCOL = "ATDP"
    
    /**
     * Describe current protocol number
     */
    const val DESCRIBE_PROTOCOL_NUMBER = "ATDPN"
    
    /**
     * Turn off echo
     */
    const val ECHO_OFF = "ATE0"
    
    /**
     * Turn on echo
     */
    const val ECHO_ON = "ATE1"
    
    /**
     * Turn off headers and addresses
     */
    const val HEADERS_OFF = "ATH0"
    
    /**
     * Turn on headers and addresses
     */
    const val HEADERS_ON = "ATH1"
    
    /**
     * Turn off printing spaces
     */
    const val SPACES_OFF = "ATS0"
    
    /**
     * Turn on printing spaces
     */
    const val SPACES_ON = "ATS1"
    
    /**
     * Turn off extra line feeds
     */
    const val LINEFEEDS_OFF = "ATL0"
    
    /**
     * Turn on extra line feeds
     */
    const val LINEFEEDS_ON = "ATL1"
    
    /**
     * Turn off timeout
     */
    const val TIMEOUT_OFF = "ATST00"
    
    /**
     * Set timeout (1-255 x 4ms, FF = 10.2 seconds)
     */
    fun setTimeout(timeout: Int): String {
        require(timeout in 0..255) { "Timeout must be between 0 and 255" }
        return "ATST%02X".format(timeout)
    }
    
    /**
     * Turn off slow initialization
     */
    const val SLOW_INIT_OFF = "ATSI0"
    
    /**
     * Turn on slow initialization
     */
    const val SLOW_INIT_ON = "ATSI1"
    
    /**
     * Get device description
     */
    const val GET_DESCRIPTION = "ATI"
    
    /**
     * Get device identifier
     */
    const val GET_DEVICE_ID = "AT@1"
    
    /**
     * Get voltage
     */
    const val GET_VOLTAGE = "ATRV"
    
    /**
     * Get firmware version
     */
    const val GET_FIRMWARE_VERSION = "AT@2"
    
    /**
     * Toggle monitor all
     */
    const val MONITOR_ALL = "ATMA"
    
    // ═══════════════════════════════════════════════════════════════════════════
    // OBD-II MODES AND PIDS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * OBD-II Modes
     */
    object Modes {
        const val SHOW_CURRENT_DATA = "01"  // Mode 1: Show current data
        const val SHOW_FREEZE_FRAME = "02"  // Mode 2: Show freeze frame data
        const val SHOW_DTC = "03"           // Mode 3: Show stored DTCs
        const val CLEAR_DTC = "04"          // Mode 4: Clear stored DTCs
        const val TEST_RESULTS = "05"       // Mode 5: Test results
        const val SHOW_PENDING_DTC = "07"   // Mode 7: Show pending DTCs
        const val CONTROL = "08"            // Mode 8: Control operations
        const val VEHICLE_INFO = "09"       // Mode 9: Vehicle information
        const val PERMANENT_DTC = "0A"      // Mode A: Permanent DTCs
    }
    
    /**
     * Mode 1 PIDs (Show current data)
     */
    object Mode1Pids {
        const val SUPPORTED_PIDS_1_20 = "00"      // PIDs supported 01-20
        const val MONITOR_STATUS = "01"            // Monitor status since DTCs cleared
        const val FREEZE_DTC = "02"               // Freeze DTC
        const val FUEL_SYSTEM_STATUS = "03"       // Fuel system status
        const val ENGINE_LOAD = "04"              // Calculated engine load
        const val ENGINE_COOLANT_TEMP = "05"      // Engine coolant temperature
        const val SHORT_FUEL_TRIM_1 = "06"        // Short term fuel trim - Bank 1
        const val LONG_FUEL_TRIM_1 = "07"         // Long term fuel trim - Bank 1
        const val SHORT_FUEL_TRIM_2 = "08"        // Short term fuel trim - Bank 2
        const val LONG_FUEL_TRIM_2 = "09"         // Long term fuel trim - Bank 2
        const val FUEL_PRESSURE = "0A"            // Fuel pressure
        const val INTAKE_MAP = "0B"               // Intake manifold absolute pressure
        const val ENGINE_RPM = "0C"               // Engine RPM
        const val VEHICLE_SPEED = "0D"            // Vehicle speed
        const val TIMING_ADVANCE = "0E"           // Timing advance
        const val INTAKE_AIR_TEMP = "0F"          // Intake air temperature
        const val MAF_AIR_FLOW = "10"             // MAF air flow rate
        const val THROTTLE_POSITION = "11"        // Throttle position
        const val COMMANDED_SECOND_AIR_STATUS = "12" // Commanded secondary air status
        const val OXYGEN_SENSORS_PRESENT_2_BANKS = "13" // Oxygen sensors present (2 banks)
        const val OXYGEN_SENSOR_1_VOLTAGE = "14"  // Oxygen sensor 1 voltage
        const val OXYGEN_SENSOR_2_VOLTAGE = "15"  // Oxygen sensor 2 voltage
        const val OXYGEN_SENSOR_3_VOLTAGE = "16"  // Oxygen sensor 3 voltage
        const val OXYGEN_SENSOR_4_VOLTAGE = "17"  // Oxygen sensor 4 voltage
        const val OXYGEN_SENSOR_5_VOLTAGE = "18"  // Oxygen sensor 5 voltage
        const val OXYGEN_SENSOR_6_VOLTAGE = "19"  // Oxygen sensor 6 voltage
        const val OXYGEN_SENSOR_7_VOLTAGE = "1A"  // Oxygen sensor 7 voltage
        const val OXYGEN_SENSOR_8_VOLTAGE = "1B"  // Oxygen sensor 8 voltage
        const val OBD_STANDARDS = "1C"            // OBD standards this vehicle conforms to
        const val OXYGEN_SENSORS_PRESENT_4_BANKS = "1D" // Oxygen sensors present (4 banks)
        const val AUX_INPUT_STATUS = "1E"         // Auxiliary input status
        const val ENGINE_RUN_TIME = "1F"          // Engine run time
    }
    
    /**
     * Mode 9 PIDs (Vehicle information)
     */
    object Mode9Pids {
        const val VEHICLE_ID_MESSAGE = "00"       // VIN message count
        const val VEHICLE_ID_NUMBER = "02"        // Vehicle identification number (VIN)
        const val CALIBRATION_ID_MESSAGE = "04"   // Calibration ID message count
        const val CALIBRATION_ID = "06"           // Calibration ID
        const val CVN_MESSAGE = "08"              // CVN message count
        const val CVN = "0A"                      // Calibration verification numbers (CVN)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Create an OBD-II command for reading a specific PID
     */
    fun readPid(mode: String, pid: String): String {
        return "$mode$pid"
    }
    
    /**
     * Create an OBD-II command for reading a specific PID from Mode 1
     */
    fun readMode1Pid(pid: String): String {
        return "${Modes.SHOW_CURRENT_DATA}$pid"
    }
    
    /**
     * Create an OBD-II command for reading a specific PID from Mode 9
     */
    fun readMode9Pid(pid: String): String {
        return "${Modes.VEHICLE_INFO}$pid"
    }
    
    /**
     * Create command to clear DTCs
     */
    fun clearDtcCommand(): String {
        return Modes.CLEAR_DTC
    }
    
    /**
     * Create command to read stored DTCs
     */
    fun readStoredDtcCommand(): String {
        return Modes.SHOW_DTC
    }
    
    /**
     * Create command to read pending DTCs
     */
    fun readPendingDtcCommand(): String {
        return Modes.SHOW_PENDING_DTC
    }
    
    /**
     * Format PID value with leading zero if needed
     */
    fun formatPid(pid: Int): String {
        return "%02X".format(pid)
    }
    
    /**
     * Parse response from ELM327 adapter
     */
    fun parseResponse(response: String): ResponseResult {
        val cleanResponse = response.trim().replace("SEARCHING...", "")
        
        return if (cleanResponse.contains("ERROR") || cleanResponse.contains("NODATA") || cleanResponse.contains("NO DATA")) {
            ResponseResult.Error(cleanResponse)
        } else if (cleanResponse.contains("UNABLE TO CONNECT")) {
            ResponseResult.Error("Unable to connect to vehicle")
        } else if (cleanResponse.contains("CAN ERROR")) {
            ResponseResult.Error("CAN bus error")
        } else {
            ResponseResult.Success(cleanResponse)
        }
    }
    
    /**
     * Result sealed class for command responses
     */
    sealed class ResponseResult {
        data class Success(val data: String) : ResponseResult()
        data class Error(val message: String) : ResponseResult()
    }
}