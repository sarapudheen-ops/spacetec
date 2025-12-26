// core/domain/src/main/kotlin/com/spacetec/automotive/core/domain/model/dtc/FreezeFrame.kt
package com.spacetec.obd.core.domain.model.dtc

/**
 * Represents freeze frame data captured when a DTC was set.
 * 
 * Freeze frame data contains the vehicle's operating conditions
 * at the time the DTC was triggered, which is crucial for diagnosis.
 * 
 * @property dtcCode The DTC code that triggered the freeze frame
 * @property ecuAddress The ECU that reported the DTC
 * @property timestamp When the freeze frame was captured
 * @property engineRpm Engine RPM at the time
 * @property vehicleSpeed Vehicle speed at the time
 * @property engineCoolantTemp Engine coolant temperature
 * @property intakeAirTemp Intake air temperature
 * @property mafAirFlowRate Mass air flow rate
 * @property throttlePosition Throttle position percentage
 * @property fuelPressure Fuel pressure
 * @property intakeManifoldPressure Intake manifold pressure
 * @property oxygenSensorData Oxygen sensor data
 * @property fuelTrimData Fuel trim data
 * @property engineLoad Engine load percentage
 * @property timingAdvance Timing advance
 * @property fuelLevel Fuel level percentage
 * @property ambientAirTemp Ambient air temperature
 * @property engineOilTemp Engine oil temperature
 * @property engineTorque Engine torque
 * @property additionalData Additional parameters
 */
data class FreezeFrame(
    val dtcCode: String,
    val ecuAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val engineRpm: Int? = null,
    val vehicleSpeed: Int? = null,
    val engineCoolantTemp: Int? = null,
    val intakeAirTemp: Int? = null,
    val mafAirFlowRate: Float? = null,
    val throttlePosition: Float? = null,
    val fuelPressure: Int? = null,
    val intakeManifoldPressure: Int? = null,
    val oxygenSensorData: Map<String, Any>? = null,
    val fuelTrimData: Map<String, Any>? = null,
    val engineLoad: Float? = null,
    val timingAdvance: Float? = null,
    val fuelLevel: Float? = null,
    val ambientAirTemp: Int? = null,
    val engineOilTemp: Int? = null,
    val engineTorque: Int? = null,
    val additionalData: Map<String, Any>? = null
) {
    /**
     * Gets a display-friendly timestamp.
     */
    val timestampDisplay: String
        get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    
    /**
     * Checks if engine-related data is available.
     */
    val hasEngineData: Boolean
        get() = engineRpm != null || engineCoolantTemp != null || engineLoad != null
    
    /**
     * Checks if vehicle speed data is available.
     */
    val hasSpeedData: Boolean
        get() = vehicleSpeed != null
    
    /**
     * Checks if temperature-related data is available.
     */
    val hasTemperatureData: Boolean
        get() = engineCoolantTemp != null || intakeAirTemp != null || 
                ambientAirTemp != null || engineOilTemp != null
    
    /**
     * Gets a summary of the freeze frame conditions.
     */
    fun getSummary(): String = buildString {
        append("DTC: $dtcCode\n")
        if (engineRpm != null) append("Engine RPM: $engineRpm\n")
        if (vehicleSpeed != null) append("Vehicle Speed: $vehicleSpeed km/h\n")
        if (engineCoolantTemp != null) append("Coolant Temp: $engineCoolantTemp°C\n")
        if (throttlePosition != null) append("Throttle: ${throttlePosition}%\n")
    }
}