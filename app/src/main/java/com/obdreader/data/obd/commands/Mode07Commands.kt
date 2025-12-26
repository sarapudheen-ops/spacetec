package com.obdreader.data.obd.commands

/**
 * Mode 07 command for reading pending DTCs.
 */
class Mode07Commands : OBDCommand(
    mode = 0x07,
    pid = null,
    name = "Show Pending DTCs",
    description = "Request pending diagnostic trouble codes"
)
