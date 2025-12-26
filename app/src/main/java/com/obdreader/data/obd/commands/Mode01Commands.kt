package com.obdreader.data.obd.commands

/**
 * Mode 01 commands for current powertrain diagnostic data.
 */
object Mode01Commands {
    
    class SupportedPIDs(pidRange: Int) : OBDCommand(
        mode = 0x01,
        pid = pidRange,
        name = "Supported PIDs",
        description = "PIDs supported in range ${pidRange.toString(16).uppercase()}-${(pidRange + 0x1F).toString(16).uppercase()}"
    )
    
    class EngineLoad : OBDCommand(
        mode = 0x01,
        pid = 0x04,
        name = "Calculated Engine Load",
        description = "Calculated engine load value"
    )
    
    class CoolantTemp : OBDCommand(
        mode = 0x01,
        pid = 0x05,
        name = "Engine Coolant Temperature",
        description = "Engine coolant temperature"
    )
    
    class EngineRPM : OBDCommand(
        mode = 0x01,
        pid = 0x0C,
        name = "Engine RPM",
        description = "Engine speed in rotations per minute"
    )
    
    class VehicleSpeed : OBDCommand(
        mode = 0x01,
        pid = 0x0D,
        name = "Vehicle Speed",
        description = "Current vehicle speed"
    )
    
    class ThrottlePosition : OBDCommand(
        mode = 0x01,
        pid = 0x11,
        name = "Throttle Position",
        description = "Throttle position"
    )
}
