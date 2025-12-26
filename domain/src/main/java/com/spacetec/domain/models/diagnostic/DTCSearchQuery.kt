package com.spacetec.domain.models.diagnostic

import com.spacetec.domain.models.vehicle.Vehicle
import java.io.Serializable

/**
 * DTC search query parameters for comprehensive DTC database searches.
 *
 * Provides flexible search capabilities across multiple dimensions including
 * code patterns, descriptions, vehicle context, systems, and symptoms.
 *
 * @property code Specific DTC code to search for
 * @property description Text to search in descriptions
 * @property vehicle Vehicle context for filtering
 * @property system DTC system filter
 * @property subsystem DTC subsystem filter
 * @property severity Minimum severity level
 * @property codeType Generic or manufacturer-specific filter
 * @property status DTC status filter
 * @property symptoms List of symptoms to match
 * @property systems List of affected systems
 * @property manufacturer Vehicle manufacturer filter
 * @property includeManufacturerSpecific Include manufacturer-specific codes
 * @property includeGeneric Include generic SAE codes
 * @property includeSuperseded Include superseded/outdated codes
 * @property emissionsRelated Filter for emissions-related codes
 * @property safetyRelated Filter for safety-related codes
 * @property milStatus Filter by MIL (check engine light) status
 * @property hasRepairProcedures Filter codes with repair procedures
 * @property hasTSBs Filter codes with technical service bulletins
 * @property hasFreezeFrames Filter codes with freeze frame data
 * @property yearFrom Vehicle year range start
 * @property yearTo Vehicle year range end
 * @property sortBy Sort option for results
 * @property sortOrder Sort direction
 * @property limit Maximum number of results
 * @property offset Pagination offset
 *
 * @author SpaceTec Team
 * @since 1.0.0
 */
data class DTCSearchQuery(
    val code: String? = null,
    val description: String? = null,
    val vehicle: Vehicle? = null,
    val system: DTCSystem? = null,
    val subsystem: DTCSubsystem? = null,
    val severity: DTCSeverity? = null,
    val codeType: DTCCodeType? = null,
    val status: DTCStatusFilter? = null,
    val symptoms: List<String> = emptyList(),
    val systems: List<String> = emptyList(),
    val manufacturer: String? = null,
    val includeManufacturerSpecific: Boolean = true,
    val includeGeneric: Boolean = true,
    val includeSuperseded: Boolean = false,
    val emissionsRelated: Boolean? = null,
    val safetyRelated: Boolean? = null,
    val milStatus: Boolean? = null,
    val hasRepairProcedures: Boolean? = null,
    val hasTSBs: Boolean? = null,
    val hasFreezeFrames: Boolean? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val sortBy: DTCSortOption = DTCSortOption.RELEVANCE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val limit: Int = 50,
    val offset: Int = 0
) : Serializable {

    /**
     * Whether this is a simple code lookup.
     */
    val isSimpleCodeLookup: Boolean
        get() = !code.isNullOrBlank() && 
                description.isNullOrBlank() && 
                symptoms.isEmpty() && 
                systems.isEmpty()

    /**
     * Whether this is a text-based search.
     */
    val isTextSearch: Boolean
        get() = !description.isNullOrBlank() || symptoms.isNotEmpty()

    /**
     * Whether vehicle-specific filtering is applied.
     */
    val hasVehicleFilter: Boolean
        get() = vehicle != null || manufacturer != null || yearFrom != null || yearTo != null

    /**
     * Whether system-level filtering is applied.
     */
    val hasSystemFilter: Boolean
        get() = system != null || subsystem != null || systems.isNotEmpty()

    /**
     * Whether severity filtering is applied.
     */
    val hasSeverityFilter: Boolean
        get() = severity != null

    /**
     * Whether status filtering is applied.
     */
    val hasStatusFilter: Boolean
        get() = status != null || milStatus != null

    /**
     * Whether content filtering is applied.
     */
    val hasContentFilter: Boolean
        get() = hasRepairProcedures != null || hasTSBs != null || hasFreezeFrames != null

    /**
     * Whether any filters are applied.
     */
    val hasFilters: Boolean
        get() = hasVehicleFilter || hasSystemFilter || hasSeverityFilter || 
                hasStatusFilter || hasContentFilter || emissionsRelated != null || 
                safetyRelated != null

    /**
     * Gets the effective vehicle year range.
     */
    val effectiveYearRange: IntRange?
        get() = when {
            yearFrom != null && yearTo != null -> yearFrom..yearTo
            yearFrom != null -> yearFrom..Int.MAX_VALUE
            yearTo != null -> Int.MIN_VALUE..yearTo
            vehicle != null -> vehicle.year?.let { it..it }
            else -> null
        }

    /**
     * Gets the effective manufacturer filter.
     */
    val effectiveManufacturer: String?
        get() = manufacturer ?: vehicle?.make

    /**
     * Creates a copy with updated pagination.
     */
    fun withPagination(newLimit: Int, newOffset: Int): DTCSearchQuery {
        return copy(limit = newLimit, offset = newOffset)
    }

    /**
     * Creates a copy with updated sorting.
     */
    fun withSorting(newSortBy: DTCSortOption, newSortOrder: SortOrder = SortOrder.DESCENDING): DTCSearchQuery {
        return copy(sortBy = newSortBy, sortOrder = newSortOrder)
    }

    /**
     * Creates a copy with vehicle context.
     */
    fun withVehicle(newVehicle: Vehicle): DTCSearchQuery {
        return copy(vehicle = newVehicle)
    }

    /**
     * Creates a copy with additional filters.
     */
    fun withFilters(
        newSeverity: DTCSeverity? = null,
        newSystem: DTCSystem? = null,
        newCodeType: DTCCodeType? = null
    ): DTCSearchQuery {
        return copy(
            severity = newSeverity ?: severity,
            system = newSystem ?: system,
            codeType = newCodeType ?: codeType
        )
    }

    /**
     * Gets a summary of active filters for display.
     */
    fun getFilterSummary(): List<String> {
        val filters = mutableListOf<String>()
        
        system?.let { filters.add("System: ${it.description}") }
        subsystem?.let { filters.add("Subsystem: ${it.description}") }
        severity?.let { filters.add("Severity: ${it.description}") }
        codeType?.let { filters.add("Type: ${it.description}") }
        status?.let { filters.add("Status: ${it.displayName}") }
        effectiveManufacturer?.let { filters.add("Manufacturer: $it") }
        effectiveYearRange?.let { 
            filters.add("Years: ${it.first}-${it.last}")
        }
        
        if (emissionsRelated == true) filters.add("Emissions Related")
        if (safetyRelated == true) filters.add("Safety Related")
        if (milStatus == true) filters.add("MIL On")
        if (hasRepairProcedures == true) filters.add("Has Repair Procedures")
        if (hasTSBs == true) filters.add("Has TSBs")
        
        return filters
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a simple code lookup query.
         */
        fun forCode(code: String): DTCSearchQuery {
            return DTCSearchQuery(code = code, limit = 1)
        }

        /**
         * Creates a text search query.
         */
        fun forText(searchText: String): DTCSearchQuery {
            return DTCSearchQuery(description = searchText)
        }

        /**
         * Creates a vehicle-specific query.
         */
        fun forVehicle(vehicle: Vehicle): DTCSearchQuery {
            return DTCSearchQuery(vehicle = vehicle)
        }

        /**
         * Creates a system-specific query.
         */
        fun forSystem(system: DTCSystem): DTCSearchQuery {
            return DTCSearchQuery(system = system)
        }

        /**
         * Creates a severity-filtered query.
         */
        fun forSeverity(minSeverity: DTCSeverity): DTCSearchQuery {
            return DTCSearchQuery(severity = minSeverity)
        }

        /**
         * Creates an empty query (returns all DTCs).
         */
        fun all(): DTCSearchQuery {
            return DTCSearchQuery(limit = 1000)
        }
    }
}

/**
 * Sort options for DTC search results.
 */
enum class DTCSortOption(
    val displayName: String,
    val description: String
) {
    RELEVANCE("Relevance", "Most relevant results first"),
    CODE("Code", "Sort by DTC code alphabetically"),
    SEVERITY("Severity", "Sort by severity level"),
    SYSTEM("System", "Sort by vehicle system"),
    DESCRIPTION("Description", "Sort by description alphabetically"),
    FREQUENCY("Frequency", "Sort by occurrence frequency"),
    DATE_ADDED("Date Added", "Sort by when DTC was added to database"),
    LAST_UPDATED("Last Updated", "Sort by last update date");

    /**
     * Whether this sort option supports both ascending and descending.
     */
    val supportsBothDirections: Boolean
        get() = this != RELEVANCE
}

/**
 * Sort order direction.
 */
enum class SortOrder(
    val displayName: String
) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

/**
 * DTC status filter options.
 */
enum class DTCStatusFilter(
    val displayName: String,
    val description: String
) {
    ACTIVE("Active", "Currently active DTCs"),
    PENDING("Pending", "Pending confirmation DTCs"),
    CONFIRMED("Confirmed", "Confirmed/stored DTCs"),
    PERMANENT("Permanent", "Permanent DTCs"),
    HISTORICAL("Historical", "Historical/cleared DTCs"),
    MIL_ON("MIL On", "DTCs with check engine light on"),
    ANY("Any Status", "DTCs with any status")
}

/**
 * DTC pattern analysis query for identifying trends and patterns.
 *
 * @property timeRange Time range for analysis
 * @property vehicleIds Specific vehicles to analyze
 * @property dtcCodes Specific DTCs to analyze
 * @property manufacturers Manufacturers to include
 * @property modelYears Model years to include
 * @property includeSeasonalAnalysis Include seasonal trend analysis
 * @property includeCorrelationAnalysis Include DTC correlation analysis
 * @property includeFrequencyAnalysis Include frequency analysis
 * @property minOccurrences Minimum occurrences for pattern detection
 * @property confidenceThreshold Minimum confidence level for patterns
 */
data class DTCPatternAnalysisQuery(
    val timeRange: TimeRange,
    val vehicleIds: List<String> = emptyList(),
    val dtcCodes: List<String> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val modelYears: List<Int> = emptyList(),
    val includeSeasonalAnalysis: Boolean = true,
    val includeCorrelationAnalysis: Boolean = true,
    val includeFrequencyAnalysis: Boolean = true,
    val minOccurrences: Int = 5,
    val confidenceThreshold: Double = 0.7
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Time range for queries and analysis.
 *
 * @property startTime Start timestamp (milliseconds)
 * @property endTime End timestamp (milliseconds)
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long
) : Serializable {

    /**
     * Duration in milliseconds.
     */
    val durationMs: Long
        get() = endTime - startTime

    /**
     * Duration in days.
     */
    val durationDays: Long
        get() = durationMs / (24 * 60 * 60 * 1000)

    /**
     * Whether this range contains a specific timestamp.
     */
    fun contains(timestamp: Long): Boolean {
        return timestamp in startTime..endTime
    }

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Creates a range for the last N days.
         */
        fun lastDays(days: Int): TimeRange {
            val end = System.currentTimeMillis()
            val start = end - (days * 24 * 60 * 60 * 1000L)
            return TimeRange(start, end)
        }

        /**
         * Creates a range for the last N months.
         */
        fun lastMonths(months: Int): TimeRange {
            val end = System.currentTimeMillis()
            val start = end - (months * 30L * 24 * 60 * 60 * 1000L)
            return TimeRange(start, end)
        }

        /**
         * Creates a range for the current year.
         */
        fun currentYear(): TimeRange {
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            
            return TimeRange(calendar.timeInMillis, now)
        }
    }
}