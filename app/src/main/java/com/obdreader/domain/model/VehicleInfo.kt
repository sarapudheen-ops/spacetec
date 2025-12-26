package com.obdreader.domain.model

/**
 * Vehicle information from Mode 09 PIDs.
 */
data class VehicleInfo(
    val vin: String? = null,
    val manufacturer: String? = null,
    val year: Int? = null,
    val plantCode: Char? = null,
    val serialNumber: String? = null,
    val countryOfOrigin: String? = null,
    val vehicleType: String? = null,
    val calibrationIds: List<String> = emptyList(),
    val cvns: List<String> = emptyList(),
    val ecuNames: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
