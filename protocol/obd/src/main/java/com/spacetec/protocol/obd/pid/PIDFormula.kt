package com.spacetec.protocol.obd.pid

/**
 * PID Formula Calculator
 * Implements all PID formulas according to SAE J1979 standard
 */
object PIDFormula {
    
    /**
     * Calculate engine coolant temperature
     * Formula: A - 40 (°C)
     * PID: 0x05
     */
    fun calculateEngineCoolantTemperature(a: Int): Float {
        return (a and 0xFF) - 40.0f
    }
    
    /**
     * Calculate engine RPM
     * Formula: (256*A + B) / 4
     * PID: 0x0C
     */
    fun calculateEngineRPM(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue / 4.0f
    }
    
    /**
     * Calculate vehicle speed
     * Formula: A (km/h)
     * PID: 0x0D
     */
    fun calculateVehicleSpeed(a: Int): Int {
        return a and 0xFF
    }
    
    /**
     * Calculate intake air temperature
     * Formula: A - 40 (°C)
     * PID: 0x0F
     */
    fun calculateIntakeAirTemperature(a: Int): Float {
        return (a and 0xFF) - 40.0f
    }
    
    /**
     * Calculate mass air flow sensor
     * Formula: (256*A + B) / 100
     * PID: 0x10
     */
    fun calculateMassAirFlow(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue / 100.0f
    }
    
    /**
     * Calculate throttle position
     * Formula: (100 * A) / 255 (%)
     * PID: 0x11
     */
    fun calculateThrottlePosition(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate oxygen sensor voltage
     * Formula: A / 200 (V)
     * PID: 0x14 (for example)
     */
    fun calculateOxygenSensorVoltage(a: Int): Float {
        return (a and 0xFF) / 200.0f
    }
    
    /**
     * Calculate oxygen sensor short term fuel trim
     * Formula: (A - 128) * 100 / 128 (%)
     * PID: 0x14 (for example)
     */
    fun calculateOxygenSensorFuelTrim(a: Int): Float {
        return ((a and 0xFF) - 128) * 100.0f / 128.0f
    }
    
    /**
     * Calculate calculated engine load
     * Formula: (100 * A) / 255 (%)
     * PID: 0x04
     */
    fun calculateEngineLoad(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate fuel pressure
     * Formula: A * 3 (kPa)
     * PID: 0x0A
     */
    fun calculateFuelPressure(a: Int): Int {
        return (a and 0xFF) * 3
    }
    
    /**
     * Calculate intake manifold absolute pressure
     * Formula: A (kPa)
     * PID: 0x0B
     */
    fun calculateIntakeManifoldPressure(a: Int): Int {
        return a and 0xFF
    }
    
    /**
     * Calculate timing advance
     * Formula: (A - 128) / 2 (° before TDC)
     * PID: 0x0E
     */
    fun calculateTimingAdvance(a: Int): Float {
        return ((a and 0xFF) - 128) / 2.0f
    }
    
    /**
     * Calculate distance traveled with MIL on
     * Formula: 256*A + B (km)
     * PID: 0x21
     */
    fun calculateDistanceWithMIL(a: Int, b: Int): Int {
        return ((a and 0xFF) shl 8) or (b and 0xFF)
    }
    
    /**
     * Calculate fuel rail pressure (relative to manifold vacuum)
     * Formula: (256*A + B) * 0.079 (kPa)
     * PID: 0x22
     */
    fun calculateFuelRailPressure(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue * 0.079f
    }
    
    /**
     * Calculate fuel rail pressure (direct injection)
     * Formula: (256*A + B) * 10 (kPa)
     * PID: 0x23
     */
    fun calculateFuelRailPressureDirect(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue * 10.0f
    }
    
    /**
     * Calculate commanded evaporative purge
     * Formula: (100 * A) / 255 (%)
     * PID: 0x2E
     */
    fun calculateCommandedEvapPurge(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate fuel level input
     * Formula: (100 * A) / 255 (%)
     * PID: 0x2F
     */
    fun calculateFuelLevel(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate number of warm-ups since codes cleared
     * Formula: A
     * PID: 0x30
     */
    fun calculateWarmUpsSinceClear(a: Int): Int {
        return a and 0xFF
    }
    
    /**
     * Calculate distance traveled since codes cleared
     * Formula: 256*A + B (km)
     * PID: 0x31
     */
    fun calculateDistanceSinceClear(a: Int, b: Int): Int {
        return ((a and 0xFF) shl 8) or (b and 0xFF)
    }
    
    /**
     * Calculate evap. system vapor pressure
     * Formula: (256*A + B - 32767) / 4 (Pa)
     * PID: 0x32
     */
    fun calculateEvapSystemVaporPressure(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return (rawValue - 32767) / 4.0f
    }
    
    /**
     * Calculate absolute barometric pressure
     * Formula: A (kPa)
     * PID: 0x33
     */
    fun calculateBarometricPressure(a: Int): Int {
        return a and 0xFF
    }
    
    /**
     * Calculate control module voltage
     * Formula: (256*A + B) / 1000 (V)
     * PID: 0x42
     */
    fun calculateControlModuleVoltage(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue / 1000.0f
    }
    
    /**
     * Calculate absolute load value
     * Formula: (256*A + B) * 100 / 255 (%)
     * PID: 0x43
     */
    fun calculateAbsoluteLoadValue(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return (rawValue * 100.0f) / 255.0f
    }
    
    /**
     * Calculate fuel/air commanded equivalence ratio
     * Formula: (256*A + B) / 32768
     * PID: 0x44
     */
    fun calculateFuelAirRatio(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue / 32768.0f
    }
    
    /**
     * Calculate relative throttle position
     * Formula: (100 * A) / 255 (%)
     * PID: 0x45
     */
    fun calculateRelativeThrottlePosition(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate ambient air temperature
     * Formula: A - 40 (°C)
     * PID: 0x46
     */
    fun calculateAmbientAirTemperature(a: Int): Float {
        return (a and 0xFF) - 40.0f
    }
    
    /**
     * Calculate absolute throttle position B
     * Formula: (100 * A) / 255 (%)
     * PID: 0x47
     */
    fun calculateAbsoluteThrottlePositionB(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate absolute throttle position C
     * Formula: (100 * A) / 255 (%)
     * PID: 0x48
     */
    fun calculateAbsoluteThrottlePositionC(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate accelerator pedal position D
     * Formula: (100 * A) / 255 (%)
     * PID: 0x49
     */
    fun calculateAcceleratorPedalPositionD(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate accelerator pedal position E
     * Formula: (100 * A) / 255 (%)
     * PID: 0x4A
     */
    fun calculateAcceleratorPedalPositionE(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate accelerator pedal position F
     * Formula: (100 * A) / 255 (%)
     * PID: 0x4B
     */
    fun calculateAcceleratorPedalPositionF(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate commanded throttle actuator
     * Formula: (100 * A) / 255 (%)
     * PID: 0x4C
     */
    fun calculateCommandedThrottleActuator(a: Int): Float {
        return (100.0f * (a and 0xFF)) / 255.0f
    }
    
    /**
     * Calculate time run with MIL on
     * Formula: 256*A + B (minutes)
     * PID: 0x4D
     */
    fun calculateTimeRunWithMIL(a: Int, b: Int): Int {
        return ((a and 0xFF) shl 8) or (b and 0xFF)
    }
    
    /**
     * Calculate time since codes cleared
     * Formula: 256*A + B (minutes)
     * PID: 0x4E
     */
    fun calculateTimeSinceCodesCleared(a: Int, b: Int): Int {
        return ((a and 0xFF) shl 8) or (b and 0xFF)
    }
    
    /**
     * Calculate maximum value for fuel-air equivalence ratio, oxygen sensor voltage, oxygen sensor current, and intake manifold absolute pressure
     * Formula: Various components in 6 bytes
     * PID: 0x4F
     */
    fun calculateMaxValues(data: ByteArray): Map<String, Float> {
        if (data.size < 6) return emptyMap()
        
        val maxFuelAirRatio = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val maxOxygenSensorVoltage = data[2].toInt() and 0xFF
        val maxOxygenSensorCurrent = data[3].toInt() and 0xFF
        val maxIntakeManifoldPressure = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        
        return mapOf(
            "maxFuelAirRatio" to (maxFuelAirRatio / 32768.0f),
            "maxOxygenSensorVoltage" to (maxOxygenSensorVoltage / 200.0f),
            "maxOxygenSensorCurrent" to ((maxOxygenSensorCurrent - 128) * 128.0f / 256.0f),
            "maxIntakeManifoldPressure" to (maxIntakeManifoldPressure / 255.0f)
        )
    }
    
    /**
     * Calculate fuel rate
     * Formula: (256*A + B) * 0.05 (L/h)
     * PID: 0x5E
     */
    fun calculateFuelRate(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue * 0.05f
    }
    
    /**
     * Calculate engine fuel rate
     * Formula: (256*A + B) * 0.05 (L/h)
     * PID: 0x5F
     */
    fun calculateEngineFuelRate(a: Int, b: Int): Float {
        val rawValue = ((a and 0xFF) shl 8) or (b and 0xFF)
        return rawValue * 0.05f
    }
    
    /**
     * Calculate engine torque percent
     * Formula: A - 125 (%)
     * PID: 0x61
     */
    fun calculateEngineTorquePercent(a: Int): Int {
        return (a and 0xFF) - 125
    }
    
    /**
     * Calculate engine reference torque
     * Formula: 256*A + B (Nm)
     * PID: 0x62
     */
    fun calculateEngineReferenceTorque(a: Int, b: Int): Int {
        return ((a and 0xFF) shl 8) or (b and 0xFF)
    }
    
    /**
     * Generic formula for PIDs that return a simple value
     */
    fun calculateGenericValue(data: ByteArray): Float {
        if (data.isEmpty()) return 0.0f
        
        // Use the first byte as a percentage value
        return (data[0].toInt() and 0xFF) * 100.0f / 255.0f
    }
}