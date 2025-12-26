package com.obdreader.data.obd.parser

/**
 * Formula implementations for parsing OBD-II PID data.
 * All formulas follow SAE J1979 specifications.
 */
object PIDFormulas {
    
    // Helper to safely get unsigned byte value
    private fun ByteArray.uByteAt(index: Int): Int = this[index].toInt() and 0xFF
    
    // ========== PIDs 00-1F ==========
    
    /** PID 04: Calculated Engine Load (%) */
    val CALCULATED_ENGINE_LOAD: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 05: Engine Coolant Temperature (°C) */
    val ENGINE_COOLANT_TEMP: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 40.0
    }
    
    /** PID 06: Short Term Fuel Trim Bank 1 (%) */
    val SHORT_TERM_FUEL_TRIM_B1: (ByteArray) -> Double = { data ->
        (data.uByteAt(0) - 128) * (100.0 / 128.0)
    }
    
    /** PID 07: Long Term Fuel Trim Bank 1 (%) */
    val LONG_TERM_FUEL_TRIM_B1: (ByteArray) -> Double = { data ->
        (data.uByteAt(0) - 128) * (100.0 / 128.0)
    }
    
    /** PID 0A: Fuel Pressure (kPa) */
    val FUEL_PRESSURE: (ByteArray) -> Double = { data ->
        3.0 * data.uByteAt(0)
    }
    
    /** PID 0B: Intake Manifold Absolute Pressure (kPa) */
    val INTAKE_MANIFOLD_PRESSURE: (ByteArray) -> Double = { data ->
        data.uByteAt(0).toDouble()
    }
    
    /** PID 0C: Engine RPM */
    val ENGINE_RPM: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)) / 4.0
    }
    
    /** PID 0D: Vehicle Speed (km/h) */
    val VEHICLE_SPEED: (ByteArray) -> Double = { data ->
        data.uByteAt(0).toDouble()
    }
    
    /** PID 0E: Timing Advance (°) */
    val TIMING_ADVANCE: (ByteArray) -> Double = { data ->
        (data.uByteAt(0) - 128) / 2.0
    }
    
    /** PID 0F: Intake Air Temperature (°C) */
    val INTAKE_AIR_TEMP: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 40.0
    }
    
    /** PID 10: MAF Air Flow Rate (g/s) */
    val MAF_AIR_FLOW: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)) / 100.0
    }
    
    /** PID 11: Throttle Position (%) */
    val THROTTLE_POSITION: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 1F: Run Time Since Engine Start (seconds) */
    val RUN_TIME: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
    
    // ========== PIDs 20-3F ==========
    
    /** PID 21: Distance Traveled with MIL On (km) */
    val DISTANCE_WITH_MIL: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
    
    /** PID 2C: Commanded EGR (%) */
    val COMMANDED_EGR: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 2F: Fuel Tank Level (%) */
    val FUEL_TANK_LEVEL: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 30: Warm-ups Since Codes Cleared */
    val WARMUPS_SINCE_CLEAR: (ByteArray) -> Double = { data ->
        data.uByteAt(0).toDouble()
    }
    
    /** PID 31: Distance Traveled Since Codes Cleared (km) */
    val DISTANCE_SINCE_CLEAR: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
    
    /** PID 33: Barometric Pressure (kPa) */
    val BAROMETRIC_PRESSURE: (ByteArray) -> Double = { data ->
        data.uByteAt(0).toDouble()
    }
    
    // ========== PIDs 40-5F ==========
    
    /** PID 42: Control Module Voltage (V) */
    val CONTROL_MODULE_VOLTAGE: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)) / 1000.0
    }
    
    /** PID 43: Absolute Load Value (%) */
    val ABSOLUTE_LOAD: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)) * (100.0 / 255.0)
    }
    
    /** PID 45: Relative Throttle Position (%) */
    val RELATIVE_THROTTLE_POS: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 46: Ambient Air Temperature (°C) */
    val AMBIENT_AIR_TEMP: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 40.0
    }
    
    /** PID 4C: Commanded Throttle Actuator (%) */
    val COMMANDED_THROTTLE: (ByteArray) -> Double = { data ->
        (100.0 / 255.0) * data.uByteAt(0)
    }
    
    /** PID 4D: Time Run with MIL On (minutes) */
    val TIME_WITH_MIL: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
    
    /** PID 4E: Time Since Codes Cleared (minutes) */
    val TIME_SINCE_CLEAR: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
    
    /** PID 5C: Engine Oil Temperature (°C) */
    val ENGINE_OIL_TEMP: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 40.0
    }
    
    /** PID 5E: Engine Fuel Rate (L/h) */
    val ENGINE_FUEL_RATE: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)) * 0.05
    }
    
    // ========== PIDs 60-7F ==========
    
    /** PID 61: Driver's Demand Engine - Percent Torque (%) */
    val DRIVER_DEMAND_TORQUE: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 125.0
    }
    
    /** PID 62: Actual Engine - Percent Torque (%) */
    val ACTUAL_ENGINE_TORQUE: (ByteArray) -> Double = { data ->
        data.uByteAt(0) - 125.0
    }
    
    /** PID 63: Engine Reference Torque (Nm) */
    val ENGINE_REFERENCE_TORQUE: (ByteArray) -> Double = { data ->
        ((data.uByteAt(0) * 256) + data.uByteAt(1)).toDouble()
    }
}
