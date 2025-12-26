package com.obdreader.data.obd.commands

/**
 * Mode 09 commands for vehicle information.
 */
object Mode09Commands {
    
    class VehicleVIN : OBDCommand(
        mode = 0x09,
        pid = 0x02,
        name = "Vehicle VIN",
        description = "Vehicle identification number"
    )
    
    class CalibrationID : OBDCommand(
        mode = 0x09,
        pid = 0x04,
        name = "Calibration ID",
        description = "Calibration identification"
    )
    
    class ECUName : OBDCommand(
        mode = 0x09,
        pid = 0x0A,
        name = "ECU Name",
        description = "ECU name"
    )
}
