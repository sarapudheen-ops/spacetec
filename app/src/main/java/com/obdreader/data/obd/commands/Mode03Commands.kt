package com.obdreader.data.obd.commands

/**
 * Mode 03 command for reading stored DTCs.
 */
class Mode03Commands : OBDCommand(
    mode = 0x03,
    pid = null,
    name = "Show Stored DTCs",
    description = "Request stored diagnostic trouble codes"
)
