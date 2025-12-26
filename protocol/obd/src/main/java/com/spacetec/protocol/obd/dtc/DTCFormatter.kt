package com.spacetec.protocol.obd.dtc

import com.spacetec.protocol.obd.DTC
import com.spacetec.protocol.obd.DTCSeverity
import com.spacetec.protocol.obd.DTCStatus

/**
 * DTC Formatter
 * Formats DTC information for display and reporting
 */
object DTCFormatter {
    
    /**
     * Format a single DTC for display
     * @param dtc The DTC object to format
     * @param includeStatus Whether to include status information
     * @param includeSeverity Whether to include severity information
     * @return Formatted DTC string
     */
    fun formatDTC(dtc: DTC, includeStatus: Boolean = true, includeSeverity: Boolean = true): String {
        val parts = mutableListOf<String>()
        
        parts.add(dtc.code)
        
        if (includeSeverity) {
            parts.add("(${formatSeverity(dtc.severity)})")
        }
        
        parts.add(dtc.description)
        
        if (includeStatus) {
            parts.add("[${formatStatus(dtc.status)}]")
        }
        
        return parts.joinToString(" ")
    }
    
    /**
     * Format a list of DTCs
     * @param dtcs List of DTC objects to format
     * @param includeStatus Whether to include status information
     * @param includeSeverity Whether to include severity information
     * @return Formatted string with all DTCs
     */
    fun formatDTCs(dtcs: List<DTC>, includeStatus: Boolean = true, includeSeverity: Boolean = true): String {
        if (dtcs.isEmpty()) {
            return "No DTCs found"
        }
        
        return dtcs.joinToString("\n") { formatDTC(it, includeStatus, includeSeverity) }
    }
    
    /**
     * Format DTC severity for display
     */
    private fun formatSeverity(severity: DTCSeverity): String {
        return when (severity) {
            DTCSeverity.LOW -> "LOW"
            DTCSeverity.MEDIUM -> "MEDIUM"
            DTCSeverity.HIGH -> "HIGH"
            DTCSeverity.CRITICAL -> "CRITICAL"
        }
    }
    
    /**
     * Format DTC status for display
     */
    private fun formatStatus(status: DTCStatus): String {
        val statusParts = mutableListOf<String>()
        
        if (status.isCurrentlyActive) statusParts.add("ACTIVE")
        if (status.isPending) statusParts.add("PENDING")
        if (status.isStored) statusParts.add("STORED")
        if (status.isTestFailed) statusParts.add("FAILED")
        if (status.isTestFailedThisCycle) statusParts.add("FAILED_THIS_CYCLE")
        if (status.isConfirmed) statusParts.add("CONFIRMED")
        
        return statusParts.joinToString("|")
    }
    
    /**
     * Format DTCs grouped by severity
     */
    fun formatDTCsBySeverity(dtcs: List<DTC>): Map<DTCSeverity, List<DTC>> {
        return dtcs.groupBy { it.severity }
    }
    
    /**
     * Format DTCs grouped by type (P, C, B, U)
     */
    fun formatDTCsByType(dtcs: List<DTC>): Map<String, List<DTC>> {
        return dtcs.groupBy { dtc ->
            if (dtc.code.isNotEmpty()) {
                dtc.code.substring(0, 1)
            } else {
                "UNKNOWN"
            }
        }
    }
    
    /**
     * Format DTCs grouped by emissions relevance
     */
    fun formatDTCsByEmissionsRelevance(dtcs: List<DTC>): Map<String, List<DTC>> {
        return dtcs.groupBy { dtc ->
            if (DTCParser.isEmissionsRelated(dtc.code)) {
                "EMISSIONS"
            } else {
                "NON_EMISSIONS"
            }
        }
    }
    
    /**
     * Create a summary of DTCs
     */
    fun createDTCSummary(dtcs: List<DTC>): DTCSummary {
        val bySeverity = formatDTCsBySeverity(dtcs)
        val byType = formatDTCsByType(dtcs)
        val byEmissions = formatDTCsByEmissionsRelevance(dtcs)
        
        return DTCSummary(
            total = dtcs.size,
            critical = bySeverity[DTCSeverity.CRITICAL]?.size ?: 0,
            high = bySeverity[DTCSeverity.HIGH]?.size ?: 0,
            medium = bySeverity[DTCSeverity.MEDIUM]?.size ?: 0,
            low = bySeverity[DTCSeverity.LOW]?.size ?: 0,
            powertrain = byType["P"]?.size ?: 0,
            chassis = byType["C"]?.size ?: 0,
            body = byType["B"]?.size ?: 0,
            network = byType["U"]?.size ?: 0,
            emissionsRelated = byEmissions["EMISSIONS"]?.size ?: 0,
            nonEmissionsRelated = byEmissions["NON_EMISSIONS"]?.size ?: 0
        )
    }
    
    /**
     * Format DTC summary as a readable string
     */
    fun formatDTCSummary(summary: DTCSummary): String {
        return """
            DTC Summary:
            Total DTCs: ${summary.total}
            Critical: ${summary.critical}
            High: ${summary.high}
            Medium: ${summary.medium}
            Low: ${summary.low}
            Powertrain (P): ${summary.powertrain}
            Chassis (C): ${summary.chassis}
            Body (B): ${summary.body}
            Network (U): ${summary.network}
            Emissions Related: ${summary.emissionsRelated}
            Non-Emissions Related: ${summary.nonEmissionsRelated}
        """.trimIndent()
    }
    
    /**
     * DTC Summary data class
     */
    data class DTCSummary(
        val total: Int,
        val critical: Int,
        val high: Int,
        val medium: Int,
        val low: Int,
        val powertrain: Int,
        val chassis: Int,
        val body: Int,
        val network: Int,
        val emissionsRelated: Int,
        val nonEmissionsRelated: Int
    )
}