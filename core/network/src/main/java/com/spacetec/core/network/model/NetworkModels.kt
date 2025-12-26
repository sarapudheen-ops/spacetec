package com.spacetec.core.network.model

data class DTCDefinitionResponse(
    val code: String,
    val description: String,
    val explanation: String,
    val category: String,
    val system: String,
    val severity: String,
    val possibleCauses: List<String>,
    val symptoms: List<String>,
    val diagnosticSteps: List<String>,
    val repairCostEstimate: String?,
    val isEmissionRelated: Boolean
)

data class VehicleInfoResponse(
    val vin: String,
    val make: String,
    val model: String,
    val year: Int,
    val engine: String?,
    val transmission: String?,
    val supportedProtocols: List<String>
)
