package com.obdreader.data.obd.commands

/**
 * Mode 04 command for clearing DTCs.
 */
class Mode04Commands : OBDCommand(
    mode = 0x04,
    pid = null,
    name = "Clear DTCs",
    description = "Clear diagnostic trouble codes and freeze frame data"
)
