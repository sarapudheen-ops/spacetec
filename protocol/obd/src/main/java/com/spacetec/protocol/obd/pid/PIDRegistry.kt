package com.spacetec.protocol.obd.pid

/**
 * PID Registry
 * Contains information about all supported PIDs according to SAE J1979
 */
object PIDRegistry {
    
    /**
     * PID Definition data class
     */
    data class PIDDefinition(
        val pid: Int,
        val name: String,
        val description: String,
        val bytesRequired: Int,
        val unit: String,
        val formula: (ByteArray) -> Any
    )
    
    /**
     * Map of all standard PIDs
     */
    private val standardPIDs = mapOf(
        // Standard PIDs (0x00-0x1F)
        0x00 to PIDDefinition(0x00, "PIDS_01_20", "PID Support 01-20", 4, "bitarray", { it }),
        
        // Monitor status since DTCs cleared
        0x01 to PIDDefinition(0x01, "MONITOR_STATUS", "Monitor status since DTCs cleared", 4, "bitarray", { it }),
        
        // Freeze DTC
        0x02 to PIDDefinition(0x02, "FREEZE_DTC", "Freeze DTC", 2, "string", { bytesToString(it) }),
        
        // Fuel system status
        0x03 to PIDDefinition(0x03, "FUEL_STATUS", "Fuel system status", 2, "string", { bytesToString(it) }),
        
        // Calculated engine load
        0x04 to PIDDefinition(0x04, "ENGINE_LOAD", "Calculated engine load", 1, "%", { PIDFormula.calculateEngineLoad(it[0].toInt()) }),
        
        // Engine coolant temperature
        0x05 to PIDDefinition(0x05, "COOLANT_TEMP", "Engine coolant temperature", 1, "°C", { PIDFormula.calculateEngineCoolantTemperature(it[0].toInt()) }),
        
        // Short term fuel trim - Bank 1
        0x06 to PIDDefinition(0x06, "FUEL_TRIM_1", "Short term fuel trim - Bank 1", 1, "%", { PIDFormula.calculateOxygenSensorFuelTrim(it[0].toInt()) }),
        
        // Long term fuel trim - Bank 1
        0x07 to PIDDefinition(0x07, "FUEL_TRIM_2", "Long term fuel trim - Bank 1", 1, "%", { PIDFormula.calculateOxygenSensorFuelTrim(it[0].toInt()) }),
        
        // Short term fuel trim - Bank 2
        0x08 to PIDDefinition(0x08, "FUEL_TRIM_3", "Short term fuel trim - Bank 2", 1, "%", { PIDFormula.calculateOxygenSensorFuelTrim(it[0].toInt()) }),
        
        // Long term fuel trim - Bank 2
        0x09 to PIDDefinition(0x09, "FUEL_TRIM_4", "Long term fuel trim - Bank 2", 1, "%", { PIDFormula.calculateOxygenSensorFuelTrim(it[0].toInt()) }),
        
        // Fuel pressure
        0x0A to PIDDefinition(0x0A, "FUEL_PRESSURE", "Fuel pressure", 1, "kPa", { PIDFormula.calculateFuelPressure(it[0].toInt()) }),
        
        // Intake manifold absolute pressure
        0x0B to PIDDefinition(0x0B, "INTAKE_PRESSURE", "Intake manifold absolute pressure", 1, "kPa", { PIDFormula.calculateIntakeManifoldPressure(it[0].toInt()) }),
        
        // Engine RPM
        0x0C to PIDDefinition(0x0C, "RPM", "Engine RPM", 2, "rpm", { PIDFormula.calculateEngineRPM(it[0].toInt(), it[1].toInt()) }),
        
        // Vehicle speed
        0x0D to PIDDefinition(0x0D, "VEHICLE_SPEED", "Vehicle speed", 1, "km/h", { PIDFormula.calculateVehicleSpeed(it[0].toInt()) }),
        
        // Timing advance
        0x0E to PIDDefinition(0x0E, "TIMING_ADVANCE", "Timing advance", 1, "°", { PIDFormula.calculateTimingAdvance(it[0].toInt()) }),
        
        // Intake air temperature
        0x0F to PIDDefinition(0x0F, "INTAKE_TEMP", "Intake air temperature", 1, "°C", { PIDFormula.calculateIntakeAirTemperature(it[0].toInt()) }),
        
        // Mass air flow sensor
        0x10 to PIDDefinition(0x10, "MAF", "Mass air flow sensor", 2, "g/s", { PIDFormula.calculateMassAirFlow(it[0].toInt(), it[1].toInt()) }),
        
        // Throttle position
        0x11 to PIDDefinition(0x11, "THROTTLE_POS", "Throttle position", 1, "%", { PIDFormula.calculateThrottlePosition(it[0].toInt()) }),
        
        // Commanded secondary air status
        0x12 to PIDDefinition(0x12, "AIR_STATUS", "Commanded secondary air status", 1, "string", { bytesToString(byteArrayOf(it[0])) }),
        
        // Oxygen sensors present (in 2 banks)
        0x13 to PIDDefinition(0x13, "O2_SENSORS", "Oxygen sensors present", 1, "bitarray", { it }),
        
        // Oxygen sensor 1 voltage
        0x14 to PIDDefinition(0x14, "O2_SENSOR_1", "Oxygen Sensor 1 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 2 voltage
        0x15 to PIDDefinition(0x15, "O2_SENSOR_2", "Oxygen Sensor 2 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 3 voltage
        0x16 to PIDDefinition(0x16, "O2_SENSOR_3", "Oxygen Sensor 3 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 4 voltage
        0x17 to PIDDefinition(0x17, "O2_SENSOR_4", "Oxygen Sensor 4 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 5 voltage
        0x18 to PIDDefinition(0x18, "O2_SENSOR_5", "Oxygen Sensor 5 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 6 voltage
        0x19 to PIDDefinition(0x19, "O2_SENSOR_6", "Oxygen Sensor 6 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 7 voltage
        0x1A to PIDDefinition(0x1A, "O2_SENSOR_7", "Oxygen Sensor 7 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // Oxygen sensor 8 voltage
        0x1B to PIDDefinition(0x1B, "O2_SENSOR_8", "Oxygen Sensor 8 voltage", 2, "V", { PIDFormula.calculateOxygenSensorVoltage(it[0].toInt()) }),
        
        // OBD standards this vehicle conforms to
        0x1C to PIDDefinition(0x1C, "OBD_STANDARDS", "OBD standards this vehicle conforms to", 1, "string", { bytesToString(it) }),
        
        // Oxygen sensors present (in 4 banks)
        0x1D to PIDDefinition(0x1D, "O2_SENSORS_ALT", "Oxygen sensors present (alternate)", 1, "bitarray", { it }),
        
        // Auxiliary input status
        0x1E to PIDDefinition(0x1E, "AUX_INPUT", "Auxiliary input status", 1, "string", { bytesToString(it) }),
        
        // Run time since engine start
        0x1F to PIDDefinition(0x1F, "RUN_TIME", "Run time since engine start", 2, "s", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // Standard PIDs (0x20-0x3F)
        0x20 to PIDDefinition(0x20, "PIDS_21_40", "PID Support 21-40", 4, "bitarray", { it }),
        
        // Distance traveled with MIL on
        0x21 to PIDDefinition(0x21, "DISTANCE_MIL", "Distance traveled with MIL on", 2, "km", { PIDFormula.calculateDistanceWithMIL(it[0].toInt(), it[1].toInt()) }),
        
        // Fuel rail pressure (relative to manifold vacuum)
        0x22 to PIDDefinition(0x22, "FUEL_RAIL_PRESSURE", "Fuel rail pressure (relative)", 2, "kPa", { PIDFormula.calculateFuelRailPressure(it[0].toInt(), it[1].toInt()) }),
        
        // Fuel rail pressure (direct injection)
        0x23 to PIDDefinition(0x23, "FUEL_RAIL_PRESSURE_DIR", "Fuel rail pressure (direct)", 2, "kPa", { PIDFormula.calculateFuelRailPressureDirect(it[0].toInt(), it[1].toInt()) }),
        
        // O2S1_WR_lambda Voltage
        0x24 to PIDDefinition(0x24, "O2S1_WR_LAMBDA_VOLTAGE", "O2S1_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S2_WR_lambda Voltage
        0x25 to PIDDefinition(0x25, "O2S2_WR_LAMBDA_VOLTAGE", "O2S2_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S3_WR_lambda Voltage
        0x26 to PIDDefinition(0x26, "O2S3_WR_LAMBDA_VOLTAGE", "O2S3_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S4_WR_lambda Voltage
        0x27 to PIDDefinition(0x27, "O2S4_WR_LAMBDA_VOLTAGE", "O2S4_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S5_WR_lambda Voltage
        0x28 to PIDDefinition(0x28, "O2S5_WR_LAMBDA_VOLTAGE", "O2S5_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S6_WR_lambda Voltage
        0x29 to PIDDefinition(0x29, "O2S6_WR_LAMBDA_VOLTAGE", "O2S6_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S7_WR_lambda Voltage
        0x2A to PIDDefinition(0x2A, "O2S7_WR_LAMBDA_VOLTAGE", "O2S7_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // O2S8_WR_lambda Voltage
        0x2B to PIDDefinition(0x2B, "O2S8_WR_LAMBDA_VOLTAGE", "O2S8_WR_lambda Voltage", 2, "V", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // Commanded EGR
        0x2C to PIDDefinition(0x2C, "CMD_EGR", "Commanded EGR", 1, "%", { PIDFormula.calculateEngineLoad(it[0].toInt()) }),
        
        // EGR Error
        0x2D to PIDDefinition(0x2D, "EGR_ERROR", "EGR Error", 1, "%", { PIDFormula.calculateOxygenSensorFuelTrim(it[0].toInt()) }),
        
        // Commanded evaporative purge
        0x2E to PIDDefinition(0x2E, "CMD_EVAPORATIVE_PURGE", "Commanded evaporative purge", 1, "%", { PIDFormula.calculateCommandedEvapPurge(it[0].toInt()) }),
        
        // Fuel level input
        0x2F to PIDDefinition(0x2F, "FUEL_LEVEL", "Fuel level input", 1, "%", { PIDFormula.calculateFuelLevel(it[0].toInt()) }),
        
        // Number of warm-ups since codes cleared
        0x30 to PIDDefinition(0x30, "WARMUPS_SINCE_DTC_CLEAR", "Number of warm-ups since codes cleared", 1, "", { PIDFormula.calculateWarmUpsSinceClear(it[0].toInt()) }),
        
        // Distance traveled since codes cleared
        0x31 to PIDDefinition(0x31, "DISTANCE_SINCE_DTC_CLEAR", "Distance traveled since codes cleared", 2, "km", { PIDFormula.calculateDistanceSinceClear(it[0].toInt(), it[1].toInt()) }),
        
        // Evap. system vapor pressure
        0x32 to PIDDefinition(0x32, "EVAPORATIVE_VAPOR_PRESSURE", "Evap. system vapor pressure", 2, "Pa", { PIDFormula.calculateEvapSystemVaporPressure(it[0].toInt(), it[1].toInt()) }),
        
        // Absolute barometric pressure
        0x33 to PIDDefinition(0x33, "BAROMETRIC_PRESSURE", "Absolute barometric pressure", 1, "kPa", { PIDFormula.calculateBarometricPressure(it[0].toInt()) }),
        
        // O2S1_WR_lambda Current
        0x34 to PIDDefinition(0x34, "O2S1_WR_LAMBDA_CURRENT", "O2S1_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S2_WR_lambda Current
        0x35 to PIDDefinition(0x35, "O2S2_WR_LAMBDA_CURRENT", "O2S2_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S3_WR_lambda Current
        0x36 to PIDDefinition(0x36, "O2S3_WR_LAMBDA_CURRENT", "O2S3_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S4_WR_lambda Current
        0x37 to PIDDefinition(0x37, "O2S4_WR_LAMBDA_CURRENT", "O2S4_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S5_WR_lambda Current
        0x38 to PIDDefinition(0x38, "O2S5_WR_LAMBDA_CURRENT", "O2S5_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S6_WR_lambda Current
        0x39 to PIDDefinition(0x39, "O2S6_WR_LAMBDA_CURRENT", "O2S6_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S7_WR_lambda Current
        0x3A to PIDDefinition(0x3A, "O2S7_WR_LAMBDA_CURRENT", "O2S7_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // O2S8_WR_lambda Current
        0x3B to PIDDefinition(0x3B, "O2S8_WR_LAMBDA_CURRENT", "O2S8_WR_lambda Current", 2, "mA", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) }),
        
        // Catalyst temperature: Bank 1, Sensor 1
        0x3C to PIDDefinition(0x3C, "CATALYST_TEMP_B1S1", "Catalyst temperature: Bank 1, Sensor 1", 2, "°C", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) / 10.0f - 40.0f }),
        
        // Catalyst temperature: Bank 2, Sensor 1
        0x3D to PIDDefinition(0x3D, "CATALYST_TEMP_B2S1", "Catalyst temperature: Bank 2, Sensor 1", 2, "°C", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) / 10.0f - 40.0f }),
        
        // Catalyst temperature: Bank 1, Sensor 2
        0x3E to PIDDefinition(0x3E, "CATALYST_TEMP_B1S2", "Catalyst temperature: Bank 1, Sensor 2", 2, "°C", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) / 10.0f - 40.0f }),
        
        // Catalyst temperature: Bank 2, Sensor 2
        0x3F to PIDDefinition(0x3F, "CATALYST_TEMP_B2S2", "Catalyst temperature: Bank 2, Sensor 2", 2, "°C", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) / 10.0f - 40.0f }),
        
        // Standard PIDs (0x40-0x5F)
        0x40 to PIDDefinition(0x40, "PIDS_41_60", "PID Support 41-60", 4, "bitarray", { it }),
        
        // Monitor status this drive cycle
        0x41 to PIDDefinition(0x41, "MONITOR_STATUS_DRIVE_CYCLE", "Monitor status this drive cycle", 4, "bitarray", { it }),
        
        // Control module voltage
        0x42 to PIDDefinition(0x42, "CONTROL_MODULE_VOLTAGE", "Control module voltage", 2, "V", { PIDFormula.calculateControlModuleVoltage(it[0].toInt(), it[1].toInt()) }),
        
        // Absolute load value
        0x43 to PIDDefinition(0x43, "ABSOLUTE_LOAD", "Absolute load value", 2, "%", { PIDFormula.calculateAbsoluteLoadValue(it[0].toInt(), it[1].toInt()) }),
        
        // Fuel/air commanded equivalence ratio
        0x44 to PIDDefinition(0x44, "FUEL_AIR_EQUIV_RATIO", "Fuel/air commanded equivalence ratio", 2, "", { PIDFormula.calculateFuelAirRatio(it[0].toInt(), it[1].toInt()) }),
        
        // Relative throttle position
        0x45 to PIDDefinition(0x45, "RELATIVE_THROTTLE_POS", "Relative throttle position", 1, "%", { PIDFormula.calculateRelativeThrottlePosition(it[0].toInt()) }),
        
        // Ambient air temperature
        0x46 to PIDDefinition(0x46, "AMBIENT_TEMP", "Ambient air temperature", 1, "°C", { PIDFormula.calculateAmbientAirTemperature(it[0].toInt()) }),
        
        // Absolute throttle position B
        0x47 to PIDDefinition(0x47, "ABSOLUTE_THROTTLE_POS_B", "Absolute throttle position B", 1, "%", { PIDFormula.calculateAbsoluteThrottlePositionB(it[0].toInt()) }),
        
        // Absolute throttle position C
        0x48 to PIDDefinition(0x48, "ABSOLUTE_THROTTLE_POS_C", "Absolute throttle position C", 1, "%", { PIDFormula.calculateAbsoluteThrottlePositionC(it[0].toInt()) }),
        
        // Accelerator pedal position D
        0x49 to PIDDefinition(0x49, "ACCELERATOR_PEDAL_POS_D", "Accelerator pedal position D", 1, "%", { PIDFormula.calculateAcceleratorPedalPositionD(it[0].toInt()) }),
        
        // Accelerator pedal position E
        0x4A to PIDDefinition(0x4A, "ACCELERATOR_PEDAL_POS_E", "Accelerator pedal position E", 1, "%", { PIDFormula.calculateAcceleratorPedalPositionE(it[0].toInt()) }),
        
        // Accelerator pedal position F
        0x4B to PIDDefinition(0x4B, "ACCELERATOR_PEDAL_POS_F", "Accelerator pedal position F", 1, "%", { PIDFormula.calculateAcceleratorPedalPositionF(it[0].toInt()) }),
        
        // Commanded throttle actuator
        0x4C to PIDDefinition(0x4C, "CMD_THROTTLE_ACTUATOR", "Commanded throttle actuator", 1, "%", { PIDFormula.calculateCommandedThrottleActuator(it[0].toInt()) }),
        
        // Time run with MIL on
        0x4D to PIDDefinition(0x4D, "TIME_WITH_MIL", "Time run with MIL on", 2, "min", { PIDFormula.calculateTimeRunWithMIL(it[0].toInt(), it[1].toInt()) }),
        
        // Time since codes cleared
        0x4E to PIDDefinition(0x4E, "TIME_SINCE_CODES_CLEARED", "Time since codes cleared", 2, "min", { PIDFormula.calculateTimeSinceCodesCleared(it[0].toInt(), it[1].toInt()) }),
        
        // Maximum value for fuel-air equivalence ratio, oxygen sensor voltage, oxygen sensor current, and intake manifold absolute pressure
        0x4F to PIDDefinition(0x4F, "MAX_VALUES", "Maximum values", 6, "mixed", { PIDFormula.calculateMaxValues(it) }),
        
        // Maximum value for air flow rate from mass air flow sensor
        0x50 to PIDDefinition(0x50, "MAX_MAF_RATE", "Maximum MAF rate", 1, "g/s", { (it[0].toInt() and 0xFF) * 10 }),
        
        // Fuel type
        0x51 to PIDDefinition(0x51, "FUEL_TYPE", "Fuel type", 1, "string", { bytesToString(it) }),
        
        // Ethanol fuel %
        0x52 to PIDDefinition(0x52, "ETHANOL_FUEL", "Ethanol fuel %", 1, "%", { (it[0].toInt() and 0xFF) * 100.0f / 255.0f }),
        
        // Absolute Evap system vapor pressure
        0x53 to PIDDefinition(0x53, "ABS_EVAP_VAPOR_PRESSURE", "Absolute Evap system vapor pressure", 2, "kPa", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) / 200.0f }),
        
        // Evap system vapor pressure
        0x54 to PIDDefinition(0x54, "EVAP_VAPOR_PRESSURE", "Evap system vapor pressure", 2, "Pa", { (it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) - 32767 }),
        
        // Short term secondary oxygen sensor trim bank 1 and bank 3
        0x55 to PIDDefinition(0x55, "SHORT_SEC_O2_TRIM_1_3", "Short term secondary O2 trim B1 & B3", 2, "%", { 
            val a = ((it[0].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            val b = ((it[1].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            mapOf("bank1" to a, "bank3" to b)
        }),
        
        // Long term secondary oxygen sensor trim bank 1 and bank 3
        0x56 to PIDDefinition(0x56, "LONG_SEC_O2_TRIM_1_3", "Long term secondary O2 trim B1 & B3", 2, "%", { 
            val a = ((it[0].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            val b = ((it[1].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            mapOf("bank1" to a, "bank3" to b)
        }),
        
        // Short term secondary oxygen sensor trim bank 2 and bank 4
        0x57 to PIDDefinition(0x57, "SHORT_SEC_O2_TRIM_2_4", "Short term secondary O2 trim B2 & B4", 2, "%", { 
            val a = ((it[0].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            val b = ((it[1].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            mapOf("bank2" to a, "bank4" to b)
        }),
        
        // Long term secondary oxygen sensor trim bank 2 and bank 4
        0x58 to PIDDefinition(0x58, "LONG_SEC_O2_TRIM_2_4", "Long term secondary O2 trim B2 & B4", 2, "%", { 
            val a = ((it[0].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            val b = ((it[1].toInt() and 0xFF) - 128) * 100.0f / 128.0f
            mapOf("bank2" to a, "bank4" to b)
        }),
        
        // Fuel rail pressure (absolute)
        0x59 to PIDDefinition(0x59, "FUEL_RAIL_PRESSURE_ABS", "Fuel rail pressure (absolute)", 2, "kPa", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF)) * 10.0f }),
        
        // Relative accelerator pedal position
        0x5A to PIDDefinition(0x5A, "RELATIVE_ACCELERATOR_POS", "Relative accelerator pedal position", 1, "%", { (it[0].toInt() and 0xFF) * 100.0f / 255.0f }),
        
        // Hybrid battery pack remaining life
        0x5B to PIDDefinition(0x5B, "HYBRID_BATTERY_REMAINING", "Hybrid battery pack remaining life", 1, "%", { (it[0].toInt() and 0xFF) * 100.0f / 255.0f }),
        
        // Engine oil temperature
        0x5C to PIDDefinition(0x5C, "ENGINE_OIL_TEMP", "Engine oil temperature", 1, "°C", { (it[0].toInt() and 0xFF) - 40.0f }),
        
        // Fuel injection timing
        0x5D to PIDDefinition(0x5D, "FUEL_INJECTION_TIMING", "Fuel injection timing", 2, "°", { ((it[0].toInt() and 0xFF) * 256 + (it[1].toInt() and 0xFF) - 26880) / 128.0f }),
        
        // Engine fuel rate
        0x5E to PIDDefinition(0x5E, "ENGINE_FUEL_RATE", "Engine fuel rate", 2, "L/h", { PIDFormula.calculateEngineFuelRate(it[0].toInt(), it[1].toInt()) }),
        
        // Emission requirements to which vehicle is designed
        0x5F to PIDDefinition(0x5F, "EMISSION_REQUIREMENTS", "Emission requirements", 1, "string", { bytesToString(it) })
    )
    
    /**
     * Get PID definition by PID value
     */
    fun getPIDDefinition(pid: Int): PIDDefinition? {
        return standardPIDs[pid]
    }
    
    /**
     * Check if a PID is valid
     */
    fun isValidPID(pid: Int): Boolean {
        return standardPIDs.containsKey(pid)
    }
    
    /**
     * Get the number of bytes required for a specific PID
     */
    fun getPidBytesRequired(pid: Int): Int {
        return standardPIDs[pid]?.bytesRequired ?: 1
    }
    
    /**
     * Get all available PIDs
     */
    fun getAllPIDs(): Set<Int> {
        return standardPIDs.keys
    }
    
    /**
     * Convert bytes to string representation
     */
    private fun bytesToString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "0x%02X".format(it) }
    }
}