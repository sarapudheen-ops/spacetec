package com.spacetec.features.dtc.data

import com.spacetec.features.dtc.presentation.list.DTCListItem
import com.spacetec.features.dtc.presentation.scan.ScannedDTC

internal object FakeDtcData {
    val listItems: List<DTCListItem> = listOf(
        DTCListItem(
            code = "P0300",
            description = "Random/Multiple Cylinder Misfire Detected",
            severity = "MEDIUM",
            category = "Powertrain",
            system = "Engine"
        ),
        DTCListItem(
            code = "P0420",
            description = "Catalyst System Efficiency Below Threshold (Bank 1)",
            severity = "LOW",
            category = "Emissions",
            system = "Exhaust"
        ),
        DTCListItem(
            code = "P0171",
            description = "System Too Lean (Bank 1)",
            severity = "HIGH",
            category = "Fuel",
            system = "Engine"
        )
    )

    val scanned: List<ScannedDTC> = listOf(
        ScannedDTC(code = "P0300", description = "Random/Multiple Cylinder Misfire Detected", status = "STORED"),
        ScannedDTC(code = "P0420", description = "Catalyst System Efficiency Below Threshold", status = "PENDING")
    )
}
