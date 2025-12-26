package com.spacetec.obd.core.domain.model.dtc

import kotlinx.serialization.Serializable

/**
 * Contains the definition and description of a DTC code.
 * This information comes from the DTC database.
 * 
 * @property code The DTC code (e.g., "P0301")
 * @property shortDescription Brief description of the fault
 * @property longDescription Detailed description of the fault
 * @property possibleCauses List of possible causes for this DTC
 * @property symptoms Observable symptoms related to this DTC
 * @property repairSuggestions Recommended repair procedures
 * @property affectedComponents Components that may be involved
 * @property severity Severity classification
 * @property category Functional category
 * @property isEmissionsRelated Whether this affects emissions
 * @property typicalRepairCost Estimated repair cost range
 * @property laborHours Estimated labor hours for repair
 * @property manufacturer Specific manufacturer (if manufacturer-specific)
 * @property models Applicable vehicle models (if specific)
 * @property yearRange Applicable year range (if specific)
 */
@Serializable
data class DtcDefinition(
    val code: String,
    val shortDescription: String,
    val longDescription: String = "",
    val possibleCauses: List<String> = emptyList(),
    val symptoms: List<String> = emptyList(),
    val repairSuggestions: List<String> = emptyList(),
    val affectedComponents: List<String> = emptyList(),
    val severity: DtcSeverity = DtcSeverity.INFO,
    val category: DtcCategory = DtcCategory.OTHER,
    val isEmissionsRelated: Boolean = false,
    val typicalRepairCost: RepairCostRange? = null,
    val laborHours: Float? = null,
    val manufacturer: String? = null,
    val models: List<String> = emptyList(),
    val yearRange: @kotlinx.serialization.Contextual IntRange? = null,
    val technicalNotes: String = "",
    val relatedDtcs: List<String> = emptyList(),
    val tsbs: List<String> = emptyList(),
    val recalls: List<String> = emptyList()
) {
    /**
     * Returns a combined description string.
     */
    fun getFullDescription(): String = buildString {
        append(shortDescription)
        if (longDescription.isNotEmpty()) {
            append("\n\n")
            append(longDescription)
        }
    }
    
    /**
     * Returns formatted possible causes as a string.
     */
    fun getPossibleCausesFormatted(): String =
        possibleCauses.mapIndexed { index, cause -> "${index + 1}. $cause" }
            .joinToString("\n")
    
    /**
     * Returns formatted repair suggestions as a string.
     */
    fun getRepairSuggestionsFormatted(): String =
        repairSuggestions.mapIndexed { index, suggestion -> "${index + 1}. $suggestion" }
            .joinToString("\n")
    
    companion object {
        /**
         * Creates an "unknown" definition for DTCs not in database.
         */
        fun unknown(code: String): DtcDefinition = DtcDefinition(
            code = code,
            shortDescription = "Unknown DTC",
            longDescription = "This diagnostic trouble code is not in the database. " +
                    "It may be a manufacturer-specific code.",
            severity = DtcSeverity.INFO
        )
    }
}

/**
 * Represents a range of repair costs.
 */
@Serializable
data class RepairCostRange(
    val minCost: Float,
    val maxCost: Float,
    val currency: String = "USD",
    val includesLabor: Boolean = true,
    val includesParts: Boolean = true
) {
    fun toDisplayString(): String {
        val symbol = when (currency) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> currency
        }
        return "$symbol${minCost.toInt()} - $symbol${maxCost.toInt()}"
    }
}