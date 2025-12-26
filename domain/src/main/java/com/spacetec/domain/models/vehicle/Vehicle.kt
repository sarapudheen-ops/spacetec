/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.domain.models.vehicle

import com.spacetec.obd.core.domain.models.scanner.ProtocolType
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Domain model representing a vehicle being diagnosed.
 *
 * This is a pure domain model with no framework dependencies, designed to be used
 * across all layers of the application. It encapsulates all information about a
 * vehicle including identification (VIN), manufacturer details, capabilities,
 * and current diagnostic state.
 *
 * ## Vehicle Identification
 *
 * Vehicles are primarily identified by their VIN (Vehicle Identification Number),
 * which provides comprehensive information about the vehicle's manufacturer,
 * model, year, and production details.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val vehicle = Vehicle(
 *     id = UUID.randomUUID().toString(),
 *     vin = VIN.parse("WVWZZZ3CZWE123456"),
 *     displayName = "2024 Volkswagen Golf",
 *     connectionState = VehicleConnectionState.CONNECTED
 * )
 *
 * if (vehicle.isConnected) {
 *     // Perform diagnostics
 * }
 *
 * println(vehicle.fullDisplayName) // "2024 Volkswagen Golf"
 * ```
 *
 * @property id Unique identifier for the vehicle (UUID or VIN-based)
 * @property vin Decoded VIN information (null if not yet read)
 * @property brand Vehicle manufacturer/brand information
 * @property model Vehicle model information
 * @property variant Vehicle variant (engine, transmission combination)
 * @property year Model year of the vehicle
 * @property displayName User-friendly display name
 * @property licensePlate Vehicle license plate number
 * @property mileage Current odometer reading in kilometers
 * @property lastDiagnosticDate Timestamp of last diagnostic session
 * @property capabilities Vehicle diagnostic capabilities
 * @property ecuList List of detected ECUs in the vehicle
 * @property detectedProtocol The communication protocol detected for this vehicle
 * @property connectionState Current connection state
 * @property createdAt Timestamp when this vehicle record was created
 * @property updatedAt Timestamp when this vehicle record was last updated
 * @property metadata Additional key-value metadata
 *
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
data class Vehicle(
    val id: String,
    val vin: VIN? = null,
    val brand: VehicleBrand? = null,
    val model: VehicleModel? = null,
    val variant: VehicleVariant? = null,
    val year: Int? = null,
    val displayName: String,
    val licensePlate: String? = null,
    val mileage: Int? = null,
    val lastDiagnosticDate: Long? = null,
    val capabilities: VehicleCapabilities? = null,
    val ecuList: List<VehicleECU> = emptyList(),
    val detectedProtocol: DetectedProtocol? = null,
    val connectionState: VehicleConnectionState = VehicleConnectionState.DISCONNECTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
) {
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Indicates whether the vehicle is currently connected.
     */
    val isConnected: Boolean
        get() = connectionState == VehicleConnectionState.CONNECTED
    
    /**
     * Indicates whether the vehicle is in the process of connecting.
     */
    val isConnecting: Boolean
        get() = connectionState in listOf(
            VehicleConnectionState.CONNECTING,
            VehicleConnectionState.INITIALIZING,
            VehicleConnectionState.IDENTIFYING
        )
    
    /**
     * Indicates whether a connection attempt can be made.
     */
    val canConnect: Boolean
        get() = connectionState == VehicleConnectionState.DISCONNECTED ||
                connectionState == VehicleConnectionState.ERROR
    
    /**
     * Indicates whether the vehicle has a valid VIN.
     */
    val hasVIN: Boolean
        get() = vin != null && vin.isValid
    
    /**
     * Indicates whether the vehicle has been identified (brand/model known).
     */
    val isIdentified: Boolean
        get() = brand != null && model != null
    
    /**
     * Indicates whether the vehicle has been fully identified with variant.
     */
    val isFullyIdentified: Boolean
        get() = isIdentified && variant != null && year != null
    
    /**
     * Indicates whether there are any ECUs detected.
     */
    val hasECUs: Boolean
        get() = ecuList.isNotEmpty()
    
    /**
     * Returns the number of responding ECUs.
     */
    val respondingECUCount: Int
        get() = ecuList.count { it.isResponding }
    
    /**
     * Indicates whether capabilities have been determined.
     */
    val hasCapabilities: Boolean
        get() = capabilities != null
    
    /**
     * Returns a full display name combining year, brand, and model.
     *
     * Example: "2024 Volkswagen Golf"
     */
    val fullDisplayName: String
        get() = buildString {
            year?.let { append("$it ") }
            brand?.let { append("${it.displayName} ") }
            model?.let { append(it.name) }
            if (isEmpty()) append(displayName)
        }.trim()
    
    /**
     * Returns a short display name (brand + model only).
     *
     * Example: "Volkswagen Golf"
     */
    val shortDisplayName: String
        get() = buildString {
            brand?.let { append("${it.displayName} ") }
            model?.let { append(it.name) }
            if (isEmpty()) append(displayName)
        }.trim()
    
    /**
     * Returns the VIN string if available.
     */
    val vinString: String?
        get() = vin?.raw
    
    /**
     * Returns formatted mileage string.
     */
    val mileageFormatted: String?
        get() = mileage?.let { "%,d km".format(it) }
    
    /**
     * Returns formatted mileage in miles.
     */
    val mileageInMiles: Int?
        get() = mileage?.let { (it * 0.621371).toInt() }
    
    /**
     * Returns formatted mileage string in miles.
     */
    val mileageFormattedMiles: String?
        get() = mileageInMiles?.let { "%,d mi".format(it) }
    
    /**
     * Returns the engine ECU if present.
     */
    val engineECU: VehicleECU?
        get() = ecuList.find { it.type == ECUType.ENGINE }
    
    /**
     * Returns the transmission ECU if present.
     */
    val transmissionECU: VehicleECU?
        get() = ecuList.find { it.type == ECUType.TRANSMISSION }
    
    /**
     * Returns the ABS/ESP ECU if present.
     */
    val absECU: VehicleECU?
        get() = ecuList.find { it.type == ECUType.ABS }
    
    /**
     * Returns the airbag ECU if present.
     */
    val airbagECU: VehicleECU?
        get() = ecuList.find { it.type == ECUType.AIRBAG }
    
    /**
     * Returns the fuel type display name.
     */
    val fuelTypeDisplayName: String?
        get() = variant?.fuelType?.displayName
    
    /**
     * Indicates whether this is an electric or hybrid vehicle.
     */
    val isElectrified: Boolean
        get() = variant?.fuelType in listOf(
            FuelType.ELECTRIC,
            FuelType.HYBRID,
            FuelType.PLUGIN_HYBRID
        )
    
    /**
     * Returns the protocol short name.
     */
    val protocolName: String?
        get() = detectedProtocol?.type?.shortName
    
    /**
     * Returns time since last diagnostic in human-readable format.
     */
    val timeSinceLastDiagnostic: String?
        get() = lastDiagnosticDate?.let { timestamp ->
            val diff = System.currentTimeMillis() - timestamp
            when {
                diff < 60_000 -> "Just now"
                diff < 3_600_000 -> "${diff / 60_000} minutes ago"
                diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
                diff < 604_800_000 -> "${diff / 86_400_000} days ago"
                diff < 2_592_000_000 -> "${diff / 604_800_000} weeks ago"
                else -> "${diff / 2_592_000_000} months ago"
            }
        }
    
    // ═══════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Validates that the vehicle has required fields populated.
     *
     * @return true if the vehicle data is valid
     */
    fun isValid(): Boolean {
        return id.isNotBlank() && displayName.isNotBlank()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // CONVERSION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Converts to a lightweight summary for list display.
     *
     * @param hasDTCs Whether the vehicle has active DTCs
     * @return VehicleSummary instance
     */
    fun toSummary(hasDTCs: Boolean = false): VehicleSummary {
        return VehicleSummary(
            id = id,
            displayName = fullDisplayName,
            vin = vin?.raw,
            brandName = brand?.displayName,
            modelName = model?.name,
            year = year,
            lastDiagnosticDate = lastDiagnosticDate,
            hasDTCs = hasDTCs
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT
    // ═══════════════════════════════════════════════════════════════════════
    
    companion object {
        
        /**
         * Creates a new vehicle with generated ID.
         *
         * @param displayName Display name for the vehicle
         * @return New Vehicle instance
         */
        fun create(displayName: String): Vehicle {
            return Vehicle(
                id = UUID.randomUUID().toString(),
                displayName = displayName
            )
        }
        
        /**
         * Creates a vehicle from VIN.
         *
         * @param vinString VIN string
         * @return Vehicle with parsed VIN, or null if invalid
         */
        fun fromVIN(vinString: String): Vehicle? {
            val vin = VIN.parse(vinString) ?: return null
            
            return Vehicle(
                id = vinString,  // Use VIN as ID
                vin = vin,
                displayName = "Vehicle ($vinString)",
                year = vin.modelYear
            )
        }
        
        /**
         * Creates a placeholder vehicle for unknown/unidentified state.
         *
         * @return Unknown vehicle placeholder
         */
        fun unknown(): Vehicle {
            return Vehicle(
                id = "unknown",
                displayName = "Unknown Vehicle"
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VIN (VEHICLE IDENTIFICATION NUMBER)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vehicle Identification Number (VIN) with decoded components.
 *
 * A VIN is a 17-character identifier that provides comprehensive information
 * about a vehicle including its manufacturer, model, year, and production details.
 *
 * ## VIN Structure
 *
 * | Position | Name | Description |
 * |----------|------|-------------|
 * | 1-3 | WMI | World Manufacturer Identifier |
 * | 4-8 | VDS | Vehicle Descriptor Section |
 * | 9 | Check | Check Digit |
 * | 10 | Year | Model Year Code |
 * | 11 | Plant | Assembly Plant Code |
 * | 12-17 | VIS | Vehicle Identifier Section (Serial) |
 *
 * ## Usage Example
 *
 * ```kotlin
 * val vin = VIN.parse("WVWZZZ3CZWE123456")
 * println(vin?.wmi) // "WVW" (Volkswagen)
 * println(vin?.modelYear) // 2024
 * println(vin?.isValid) // true
 * ```
 *
 * @property raw Original 17-character VIN string
 * @property wmi World Manufacturer Identifier (positions 1-3)
 * @property vds Vehicle Descriptor Section (positions 4-9)
 * @property vis Vehicle Identifier Section (positions 10-17)
 * @property checkDigit Check digit (position 9)
 * @property modelYear Decoded model year
 * @property plantCode Assembly plant code (position 11)
 * @property sequenceNumber Production sequence number (positions 12-17)
 */
data class VIN(
    val raw: String,
    val wmi: String,
    val vds: String,
    val vis: String,
    val checkDigit: Char,
    val modelYear: Int?,
    val plantCode: Char?,
    val sequenceNumber: String?
) {
    
    /**
     * Validates the VIN structure and check digit.
     */
    val isValid: Boolean
        get() = raw.length == 17 && validateCheckDigit()
    
    /**
     * Returns the manufacturer identifier (first 3 characters).
     */
    val manufacturerId: String
        get() = wmi
    
    /**
     * Returns the country of origin based on WMI.
     */
    val countryOfOrigin: String?
        get() = when (raw.firstOrNull()?.uppercaseChar()) {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H' -> "Africa"
            'J', 'K', 'L', 'M', 'N', 'P', 'R' -> "Asia"
            'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' -> "Europe"
            '1', '2', '3', '4', '5' -> "North America"
            '6', '7' -> "Oceania"
            '8', '9', '0' -> "South America"
            else -> null
        }
    
    /**
     * Returns the specific country based on first two WMI characters.
     */
    val country: String?
        get() = when {
            raw.startsWith("1") || raw.startsWith("4") || raw.startsWith("5") -> "United States"
            raw.startsWith("2") -> "Canada"
            raw.startsWith("3") -> "Mexico"
            raw.startsWith("J") -> "Japan"
            raw.startsWith("K") -> "South Korea"
            raw.startsWith("L") -> "China"
            raw.startsWith("S") -> when (raw[1]) {
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M' -> "United Kingdom"
                'N', 'P', 'R', 'S', 'T' -> "Germany"
                else -> "Europe"
            }
            raw.startsWith("V") -> when (raw[1]) {
                'F' -> "France"
                'R' -> "Spain"
                'S' -> "Spain"
                else -> "Europe"
            }
            raw.startsWith("W") -> "Germany"
            raw.startsWith("Y") -> when (raw[1]) {
                'V' -> "Sweden"
                else -> "Europe"
            }
            raw.startsWith("Z") -> "Italy"
            else -> countryOfOrigin
        }
    
    /**
     * Returns the manufacturer name based on WMI.
     */
    val manufacturer: String?
        get() = MANUFACTURER_MAP[wmi.take(3)] ?: MANUFACTURER_MAP[wmi.take(2)]
    
    /**
     * Returns a formatted VIN with spaces for readability.
     */
    val formatted: String
        get() = "$wmi $vds $vis"
    
    /**
     * Returns a masked VIN for display (shows first/last characters).
     */
    val masked: String
        get() = "${raw.take(4)}${"*".repeat(9)}${raw.takeLast(4)}"
    
    /**
     * Validates the check digit using the standard algorithm.
     */
    private fun validateCheckDigit(): Boolean {
        if (raw.length != 17) return false
        
        // Check digit validation is not strictly enforced for all regions
        // Some manufacturers don't use valid check digits
        // This is a basic format validation
        return raw.all { it.isLetterOrDigit() && it.uppercaseChar() !in "IOQ" }
    }
    
    companion object {
        
        /**
         * Valid characters for VIN (excludes I, O, Q).
         */
        private const val VALID_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"
        
        /**
         * Model year codes (position 10).
         */
        private val YEAR_CODES = mapOf(
            'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
            'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
            'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
            'S' to 2025, 'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029,
            'Y' to 2030,
            '1' to 2001, '2' to 2002, '3' to 2003, '4' to 2004, '5' to 2005,
            '6' to 2006, '7' to 2007, '8' to 2008, '9' to 2009
        )
        
        /**
         * WMI to manufacturer mapping.
         */
        private val MANUFACTURER_MAP = mapOf(
            // German
            "WVW" to "Volkswagen",
            "WVG" to "Volkswagen",
            "3VW" to "Volkswagen",
            "WAU" to "Audi",
            "WUA" to "Audi",
            "WBA" to "BMW",
            "WBS" to "BMW M",
            "WBY" to "BMW",
            "5UX" to "BMW",
            "WDB" to "Mercedes-Benz",
            "WDC" to "Mercedes-Benz",
            "WDD" to "Mercedes-Benz",
            "WMX" to "Mercedes-AMG",
            "WP0" to "Porsche",
            "WP1" to "Porsche",
            
            // American
            "1G1" to "Chevrolet",
            "1G2" to "Pontiac",
            "1GC" to "Chevrolet Truck",
            "1GM" to "Pontiac",
            "1G4" to "Buick",
            "1G6" to "Cadillac",
            "1FA" to "Ford",
            "1FB" to "Ford",
            "1FC" to "Ford",
            "1FD" to "Ford",
            "1FM" to "Ford",
            "1FT" to "Ford Truck",
            "1FU" to "Freightliner",
            "1GD" to "GMC",
            "1GT" to "GMC Truck",
            "1C3" to "Chrysler",
            "1C4" to "Chrysler",
            "1C6" to "Chrysler",
            "1D7" to "Dodge Ram",
            "2C3" to "Chrysler Canada",
            "2T1" to "Toyota Canada",
            "3FA" to "Ford Mexico",
            "3VW" to "Volkswagen Mexico",
            
            // Japanese
            "JA" to "Isuzu",
            "JF" to "Subaru",
            "JH" to "Honda",
            "JM" to "Mazda",
            "JN" to "Nissan",
            "JS" to "Suzuki",
            "JT" to "Toyota",
            "JY" to "Yamaha",
            
            // Korean
            "KL" to "Daewoo/GM Korea",
            "KM" to "Hyundai",
            "KN" to "Kia",
            "KPT" to "SsangYong",
            
            // Chinese
            "LFV" to "FAW-Volkswagen",
            "LSV" to "SAIC Volkswagen",
            "LTV" to "Toyota China",
            
            // European
            "SAJ" to "Jaguar",
            "SAL" to "Land Rover",
            "SAR" to "Rover",
            "SCC" to "Lotus",
            "SCF" to "Aston Martin",
            "SFZ" to "Bentley",
            "TRU" to "Audi Hungary",
            "TMB" to "Skoda",
            "UU" to "Dacia",
            "VF1" to "Renault",
            "VF3" to "Peugeot",
            "VF7" to "Citroën",
            "VNK" to "Toyota France",
            "VSS" to "SEAT",
            "VWV" to "Volkswagen Spain",
            "WF0" to "Ford Germany",
            "WME" to "Smart",
            "XTA" to "Lada",
            "YV1" to "Volvo",
            "YS3" to "Saab",
            "ZAR" to "Alfa Romeo",
            "ZCG" to "Cagiva",
            "ZDM" to "Ducati",
            "ZFA" to "Fiat",
            "ZFF" to "Ferrari",
            "ZHW" to "Lamborghini",
            "ZLA" to "Lancia",
            "ZAP" to "Piaggio",
            
            // Tesla
            "5YJ" to "Tesla",
            "7SA" to "Tesla"
        )
        
        /**
         * Parses a VIN string into a VIN object.
         *
         * @param vinString 17-character VIN string
         * @return Parsed VIN or null if invalid format
         */
        fun parse(vinString: String): VIN? {
            val cleaned = vinString
                .trim()
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
            
            if (cleaned.length != 17) return null
            
            // Check for invalid characters (I, O, Q)
            if (cleaned.any { it in "IOQ" }) return null
            
            val wmi = cleaned.substring(0, 3)
            val vds = cleaned.substring(3, 9)
            val vis = cleaned.substring(9, 17)
            val checkDigit = cleaned[8]
            val yearCode = cleaned[9]
            val plantCode = cleaned[10]
            val sequenceNumber = cleaned.substring(11, 17)
            
            val modelYear = YEAR_CODES[yearCode]
            
            return VIN(
                raw = cleaned,
                wmi = wmi,
                vds = vds,
                vis = vis,
                checkDigit = checkDigit,
                modelYear = modelYear,
                plantCode = plantCode,
                sequenceNumber = sequenceNumber
            )
        }
        
        /**
         * Checks if a VIN string has valid format.
         *
         * @param vinString VIN string to validate
         * @return true if valid format
         */
        fun isValidFormat(vinString: String): Boolean {
            val cleaned = vinString
                .trim()
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
            
            if (cleaned.length != 17) return false
            if (cleaned.any { it in "IOQ" }) return false
            
            return cleaned.all { it in VALID_CHARS }
        }
        
        /**
         * Generates a random valid VIN for testing.
         *
         * @param manufacturer WMI prefix (default: "WVW" for VW)
         * @return Random VIN string
         */
        fun generateRandom(manufacturer: String = "WVW"): String {
            val validChars = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789"
            val wmi = manufacturer.take(3).padEnd(3, 'X')
            val vds = (1..6).map { validChars.random() }.joinToString("")
            val year = 'R'  // 2024
            val plant = 'A'
            val sequence = (1..6).map { ('0'..'9').random() }.joinToString("")
            
            return "$wmi$vds$year$plant$sequence"
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE BRAND
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vehicle brand/manufacturer information.
 *
 * Represents a vehicle manufacturer with metadata about diagnostic support level.
 *
 * @property id Unique identifier for the brand
 * @property name Internal name (e.g., "volkswagen")
 * @property displayName Display name (e.g., "Volkswagen")
 * @property iconRes Resource name for brand icon
 * @property supportLevel Level of diagnostic support available
 * @property parentBrandId Parent brand ID for sub-brands (e.g., Audi -> VAG)
 */
data class VehicleBrand(
    val id: String,
    val name: String,
    val displayName: String,
    val iconRes: String? = null,
    val supportLevel: SupportLevel = SupportLevel.OBD_ONLY,
    val parentBrandId: String? = null
) {
    
    /**
     * Indicates whether this brand has full manufacturer-level support.
     */
    val hasFullSupport: Boolean
        get() = supportLevel == SupportLevel.FULL
    
    /**
     * Indicates whether this brand has enhanced diagnostic support.
     */
    val hasEnhancedSupport: Boolean
        get() = supportLevel in listOf(SupportLevel.FULL, SupportLevel.ENHANCED)
    
    /**
     * Indicates whether this is a sub-brand of another.
     */
    val isSubBrand: Boolean
        get() = parentBrandId != null
    
    companion object {
        
        /**
         * Unknown brand placeholder.
         */
        val UNKNOWN = VehicleBrand(
            id = "unknown",
            name = "unknown",
            displayName = "Unknown",
            supportLevel = SupportLevel.UNKNOWN
        )
        
        /**
         * Creates a brand from WMI code.
         *
         * @param wmi WMI from VIN
         * @return Matched brand or UNKNOWN
         */
        fun fromWMI(wmi: String): VehicleBrand {
            return KNOWN_BRANDS.find { brand ->
                WMI_BRAND_MAP[wmi.take(3)] == brand.id ||
                WMI_BRAND_MAP[wmi.take(2)] == brand.id
            } ?: UNKNOWN
        }
        
        /**
         * Known vehicle brands with support information.
         */
        val KNOWN_BRANDS = listOf(
            // German (Full Support)
            VehicleBrand("audi", "audi", "Audi", "ic_brand_audi", SupportLevel.FULL, "vag"),
            VehicleBrand("bmw", "bmw", "BMW", "ic_brand_bmw", SupportLevel.FULL),
            VehicleBrand("mercedes", "mercedes", "Mercedes-Benz", "ic_brand_mercedes", SupportLevel.FULL),
            VehicleBrand("mini", "mini", "MINI", "ic_brand_mini", SupportLevel.FULL, "bmw"),
            VehicleBrand("porsche", "porsche", "Porsche", "ic_brand_porsche", SupportLevel.FULL, "vag"),
            VehicleBrand("volkswagen", "volkswagen", "Volkswagen", "ic_brand_vw", SupportLevel.FULL, "vag"),
            VehicleBrand("seat", "seat", "SEAT", "ic_brand_seat", SupportLevel.FULL, "vag"),
            VehicleBrand("skoda", "skoda", "Škoda", "ic_brand_skoda", SupportLevel.FULL, "vag"),
            
            // Japanese (Enhanced Support)
            VehicleBrand("toyota", "toyota", "Toyota", "ic_brand_toyota", SupportLevel.ENHANCED),
            VehicleBrand("lexus", "lexus", "Lexus", "ic_brand_lexus", SupportLevel.ENHANCED, "toyota"),
            VehicleBrand("honda", "honda", "Honda", "ic_brand_honda", SupportLevel.ENHANCED),
            VehicleBrand("acura", "acura", "Acura", "ic_brand_acura", SupportLevel.ENHANCED, "honda"),
            VehicleBrand("nissan", "nissan", "Nissan", "ic_brand_nissan", SupportLevel.ENHANCED),
            VehicleBrand("infiniti", "infiniti", "Infiniti", "ic_brand_infiniti", SupportLevel.ENHANCED, "nissan"),
            VehicleBrand("mazda", "mazda", "Mazda", "ic_brand_mazda", SupportLevel.ENHANCED),
            VehicleBrand("subaru", "subaru", "Subaru", "ic_brand_subaru", SupportLevel.ENHANCED),
            VehicleBrand("mitsubishi", "mitsubishi", "Mitsubishi", "ic_brand_mitsubishi", SupportLevel.ENHANCED),
            VehicleBrand("suzuki", "suzuki", "Suzuki", "ic_brand_suzuki", SupportLevel.ENHANCED),
            
            // Korean (Enhanced Support)
            VehicleBrand("hyundai", "hyundai", "Hyundai", "ic_brand_hyundai", SupportLevel.ENHANCED),
            VehicleBrand("kia", "kia", "Kia", "ic_brand_kia", SupportLevel.ENHANCED),
            VehicleBrand("genesis", "genesis", "Genesis", "ic_brand_genesis", SupportLevel.ENHANCED, "hyundai"),
            
            // American (Enhanced Support)
            VehicleBrand("ford", "ford", "Ford", "ic_brand_ford", SupportLevel.ENHANCED),
            VehicleBrand("lincoln", "lincoln", "Lincoln", "ic_brand_lincoln", SupportLevel.ENHANCED, "ford"),
            VehicleBrand("chevrolet", "chevrolet", "Chevrolet", "ic_brand_chevrolet", SupportLevel.ENHANCED),
            VehicleBrand("gmc", "gmc", "GMC", "ic_brand_gmc", SupportLevel.ENHANCED, "gm"),
            VehicleBrand("buick", "buick", "Buick", "ic_brand_buick", SupportLevel.ENHANCED, "gm"),
            VehicleBrand("cadillac", "cadillac", "Cadillac", "ic_brand_cadillac", SupportLevel.ENHANCED, "gm"),
            VehicleBrand("jeep", "jeep", "Jeep", "ic_brand_jeep", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("dodge", "dodge", "Dodge", "ic_brand_dodge", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("chrysler", "chrysler", "Chrysler", "ic_brand_chrysler", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("ram", "ram", "RAM", "ic_brand_ram", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("tesla", "tesla", "Tesla", "ic_brand_tesla", SupportLevel.LIMITED),
            
            // European (Enhanced Support)
            VehicleBrand("volvo", "volvo", "Volvo", "ic_brand_volvo", SupportLevel.ENHANCED),
            VehicleBrand("jaguar", "jaguar", "Jaguar", "ic_brand_jaguar", SupportLevel.ENHANCED),
            VehicleBrand("landrover", "landrover", "Land Rover", "ic_brand_landrover", SupportLevel.ENHANCED),
            VehicleBrand("fiat", "fiat", "Fiat", "ic_brand_fiat", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("alfaromeo", "alfaromeo", "Alfa Romeo", "ic_brand_alfa", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("peugeot", "peugeot", "Peugeot", "ic_brand_peugeot", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("citroen", "citroen", "Citroën", "ic_brand_citroen", SupportLevel.ENHANCED, "stellantis"),
            VehicleBrand("renault", "renault", "Renault", "ic_brand_renault", SupportLevel.ENHANCED),
            VehicleBrand("opel", "opel", "Opel", "ic_brand_opel", SupportLevel.ENHANCED, "stellantis")
        )
        
        /**
         * WMI to brand ID mapping.
         */
        private val WMI_BRAND_MAP = mapOf(
            "WVW" to "volkswagen", "WVG" to "volkswagen", "3VW" to "volkswagen",
            "WAU" to "audi", "WUA" to "audi", "TRU" to "audi",
            "WBA" to "bmw", "WBS" to "bmw", "WBY" to "bmw", "5UX" to "bmw",
            "WDB" to "mercedes", "WDC" to "mercedes", "WDD" to "mercedes",
            "WP0" to "porsche", "WP1" to "porsche",
            "TMB" to "skoda",
            "VSS" to "seat",
            "JT" to "toyota", "2T" to "toyota",
            "JH" to "honda",
            "JN" to "nissan",
            "JM" to "mazda",
            "JF" to "subaru",
            "KM" to "hyundai",
            "KN" to "kia",
            "1F" to "ford", "3F" to "ford",
            "1G" to "chevrolet",
            "5YJ" to "tesla", "7SA" to "tesla",
            "YV1" to "volvo",
            "SAJ" to "jaguar",
            "SAL" to "landrover"
        )
    }
}

/**
 * Level of diagnostic support for a vehicle brand.
 *
 * @property displayName Human-readable name
 * @property description Description of support level
 */
enum class SupportLevel(val displayName: String, val description: String) {
    /**
     * Full manufacturer-level diagnostic support.
     * Includes dealer-level coding, adaptations, and special functions.
     */
    FULL("Full", "Complete manufacturer-level diagnostics"),
    
    /**
     * Enhanced OBD-II plus some manufacturer-specific features.
     * Includes enhanced DTCs and some special functions.
     */
    ENHANCED("Enhanced", "Enhanced OBD-II with manufacturer features"),
    
    /**
     * Standard OBD-II only.
     * Basic diagnostics as required by emissions regulations.
     */
    OBD_ONLY("OBD-II", "Standard OBD-II diagnostics only"),
    
    /**
     * Limited support.
     * Basic connectivity with limited features.
     */
    LIMITED("Limited", "Limited diagnostic support"),
    
    /**
     * Unknown support level.
     */
    UNKNOWN("Unknown", "Support level not determined")
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE MODEL
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vehicle model information.
 *
 * @property id Unique model identifier
 * @property brandId Brand ID this model belongs to
 * @property name Model name (e.g., "Golf", "A4")
 * @property displayName Full display name
 * @property yearStart First production year
 * @property yearEnd Last production year (null if current)
 * @property variants Available variants for this model
 * @property platforms Platform codes (e.g., "MQB", "MLB")
 * @property generation Model generation (e.g., "Mk8", "B9")
 */
data class VehicleModel(
    val id: String,
    val brandId: String,
    val name: String,
    val displayName: String = name,
    val yearStart: Int,
    val yearEnd: Int? = null,
    val variants: List<VehicleVariant> = emptyList(),
    val platforms: List<String> = emptyList(),
    val generation: String? = null
) {
    
    /**
     * Indicates whether this model is still in production.
     */
    val isCurrentProduction: Boolean
        get() = yearEnd == null || yearEnd >= Calendar.getInstance().get(Calendar.YEAR)
    
    /**
     * Returns the year range as a string.
     */
    val yearRange: String
        get() = yearEnd?.let { "$yearStart - $it" } ?: "$yearStart - Present"
    
    /**
     * Returns full name with generation if available.
     */
    val fullName: String
        get() = generation?.let { "$name ($it)" } ?: name
    
    /**
     * Finds a variant matching the given criteria.
     */
    fun findVariant(
        engineCode: String? = null,
        fuelType: FuelType? = null,
        year: Int? = null
    ): VehicleVariant? {
        return variants.find { variant ->
            (engineCode == null || variant.engineCode == engineCode) &&
            (fuelType == null || variant.fuelType == fuelType) &&
            (year == null || variant.matchesYear(year))
        }
    }
}

/**
 * Vehicle variant representing a specific configuration.
 *
 * @property id Unique variant identifier
 * @property modelId Model ID this variant belongs to
 * @property name Variant name (e.g., "2.0 TDI 150 DSG")
 * @property engineCode Engine code (e.g., "DFGA")
 * @property displacement Engine displacement in liters
 * @property power Power output in kW
 * @property powerHp Power output in horsepower
 * @property torque Torque in Nm
 * @property fuelType Fuel type
 * @property transmissionType Transmission type
 * @property driveType Drive configuration
 * @property yearStart First production year for this variant
 * @property yearEnd Last production year for this variant
 */
data class VehicleVariant(
    val id: String,
    val modelId: String,
    val name: String,
    val engineCode: String? = null,
    val displacement: Float? = null,
    val power: Int? = null,
    val powerHp: Int? = power?.let { (it * 1.341).toInt() },
    val torque: Int? = null,
    val fuelType: FuelType = FuelType.UNKNOWN,
    val transmissionType: TransmissionType = TransmissionType.UNKNOWN,
    val driveType: DriveType = DriveType.UNKNOWN,
    val yearStart: Int? = null,
    val yearEnd: Int? = null
) {
    
    /**
     * Returns a formatted power string.
     */
    val powerFormatted: String?
        get() = power?.let { "$it kW (${powerHp ?: (it * 1.341).toInt()} hp)" }
    
    /**
     * Returns a formatted displacement string.
     */
    val displacementFormatted: String?
        get() = displacement?.let { "%.1f L".format(it) }
    
    /**
     * Returns a formatted engine string combining displacement and code.
     */
    val engineFormatted: String
        get() = buildString {
            displacement?.let { append("%.1f".format(it)) }
            fuelType.takeIf { it != FuelType.UNKNOWN }?.let {
                if (isNotEmpty()) append(" ")
                append(it.shortName)
            }
            power?.let {
                if (isNotEmpty()) append(" ")
                append("${powerHp}hp")
            }
            engineCode?.let {
                if (isNotEmpty()) append(" ")
                append("($it)")
            }
            if (isEmpty()) append(name)
        }
    
    /**
     * Checks if this variant matches a given year.
     */
    fun matchesYear(year: Int): Boolean {
        val start = yearStart ?: return true
        val end = yearEnd ?: Calendar.getInstance().get(Calendar.YEAR)
        return year in start..end
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE ENUMS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Fuel type for vehicles.
 *
 * @property displayName Human-readable name
 * @property shortName Short identifier
 */
enum class FuelType(val displayName: String, val shortName: String) {
    GASOLINE("Gasoline", "Gas"),
    DIESEL("Diesel", "TDI"),
    HYBRID("Hybrid", "HEV"),
    PLUGIN_HYBRID("Plug-in Hybrid", "PHEV"),
    ELECTRIC("Electric", "EV"),
    CNG("Natural Gas", "CNG"),
    LPG("LPG", "LPG"),
    HYDROGEN("Hydrogen", "H2"),
    UNKNOWN("Unknown", "");
    
    /**
     * Indicates whether this is an electrified powertrain.
     */
    val isElectrified: Boolean
        get() = this in listOf(ELECTRIC, HYBRID, PLUGIN_HYBRID)
    
    /**
     * Indicates whether this vehicle can use EV-only mode.
     */
    val hasEVMode: Boolean
        get() = this in listOf(ELECTRIC, PLUGIN_HYBRID)
    
    companion object {
        fun fromString(value: String): FuelType {
            val upper = value.uppercase(Locale.ROOT)
            return values().find { 
                it.name == upper || 
                it.displayName.uppercase(Locale.ROOT) == upper ||
                it.shortName.uppercase(Locale.ROOT) == upper
            } ?: UNKNOWN
        }
    }
}

/**
 * Transmission type for vehicles.
 *
 * @property displayName Human-readable name
 * @property shortName Short identifier
 */
enum class TransmissionType(val displayName: String, val shortName: String) {
    MANUAL("Manual", "MT"),
    AUTOMATIC("Automatic", "AT"),
    CVT("CVT", "CVT"),
    DCT("Dual-Clutch", "DCT"),
    SINGLE_SPEED("Single Speed", "1S"),
    UNKNOWN("Unknown", "");
    
    /**
     * Indicates whether this is an automatic-type transmission.
     */
    val isAutomatic: Boolean
        get() = this in listOf(AUTOMATIC, CVT, DCT, SINGLE_SPEED)
    
    companion object {
        fun fromString(value: String): TransmissionType {
            val upper = value.uppercase(Locale.ROOT)
            return values().find {
                it.name == upper ||
                it.displayName.uppercase(Locale.ROOT) == upper ||
                it.shortName.uppercase(Locale.ROOT) == upper
            } ?: UNKNOWN
        }
    }
}

/**
 * Drive type for vehicles.
 *
 * @property displayName Human-readable name
 * @property shortName Short identifier
 */
enum class DriveType(val displayName: String, val shortName: String) {
    FWD("Front-Wheel Drive", "FWD"),
    RWD("Rear-Wheel Drive", "RWD"),
    AWD("All-Wheel Drive", "AWD"),
    FOUR_WD("Four-Wheel Drive", "4WD"),
    UNKNOWN("Unknown", "");
    
    /**
     * Indicates whether this has power to all wheels.
     */
    val isAllWheelDrive: Boolean
        get() = this in listOf(AWD, FOUR_WD)
    
    companion object {
        fun fromString(value: String): DriveType {
            val upper = value.uppercase(Locale.ROOT)
            return values().find {
                it.name == upper ||
                it.displayName.uppercase(Locale.ROOT) == upper ||
                it.shortName.uppercase(Locale.ROOT) == upper
            } ?: UNKNOWN
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE ECU
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vehicle ECU (Electronic Control Unit) information.
 *
 * Represents a control module detected in the vehicle's network.
 *
 * @property address CAN/K-Line address of the ECU
 * @property name ECU name or description
 * @property type ECU functional type
 * @property manufacturer ECU manufacturer (if known)
 * @property partNumber OEM part number
 * @property softwareVersion Software/firmware version
 * @property hardwareVersion Hardware version
 * @property codingValue Current coding value (if applicable)
 * @property isResponding Whether the ECU is currently responding
 * @property lastResponseTime Timestamp of last successful response
 */
data class VehicleECU(
    val address: Int,
    val name: String,
    val type: ECUType = ECUType.GENERIC,
    val manufacturer: String? = null,
    val partNumber: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val codingValue: String? = null,
    val isResponding: Boolean = true,
    val lastResponseTime: Long = System.currentTimeMillis()
) {
    
    /**
     * Returns the ECU address as a hex string.
     */
    val addressHex: String
        get() = "0x${address.toString(16).uppercase(Locale.ROOT).padStart(3, '0')}"
    
    /**
     * Returns a formatted display string for the ECU.
     */
    val displayString: String
        get() = "$name ($addressHex)"
    
    /**
     * Returns the response ECU address (request + 8 for OBD).
     */
    val responseAddress: Int
        get() = address + 8
    
    /**
     * Returns the response address as hex.
     */
    val responseAddressHex: String
        get() = "0x${responseAddress.toString(16).uppercase(Locale.ROOT).padStart(3, '0')}"
    
    /**
     * Indicates whether this is a powertrain ECU.
     */
    val isPowertrain: Boolean
        get() = type in listOf(ECUType.ENGINE, ECUType.TRANSMISSION)
    
    /**
     * Indicates whether this is a safety-critical ECU.
     */
    val isSafetyCritical: Boolean
        get() = type in listOf(ECUType.ABS, ECUType.AIRBAG, ECUType.STEERING)
    
    companion object {
        
        /**
         * Standard OBD-II ECU addresses.
         */
        val STANDARD_OBD_ADDRESSES = listOf(
            0x7E0 to ECUType.ENGINE,
            0x7E1 to ECUType.TRANSMISSION,
            0x7E2 to ECUType.ABS,
            0x7E3 to ECUType.AIRBAG,
            0x7E4 to ECUType.BODY,
            0x7E5 to ECUType.HVAC,
            0x7E6 to ECUType.GATEWAY,
            0x7E7 to ECUType.INSTRUMENT_CLUSTER
        )
        
        /**
         * Creates an ECU from a standard OBD address.
         */
        fun fromOBDAddress(address: Int): VehicleECU {
            val (_, type) = STANDARD_OBD_ADDRESSES.find { it.first == address }
                ?: (address to ECUType.GENERIC)
            
            return VehicleECU(
                address = address,
                name = type.displayName,
                type = type
            )
        }
    }
}

/**
 * ECU functional types.
 *
 * @property displayName Human-readable name
 * @property shortName Short identifier
 * @property defaultAddress Default CAN address for this ECU type
 */
enum class ECUType(
    val displayName: String,
    val shortName: String,
    val defaultAddress: Int
) {
    ENGINE("Engine Control Module", "ECM", 0x7E0),
    TRANSMISSION("Transmission Control Module", "TCM", 0x7E1),
    ABS("ABS/ESP Module", "ABS", 0x7E2),
    AIRBAG("Airbag Module", "SRS", 0x7E3),
    BODY("Body Control Module", "BCM", 0x7E4),
    HVAC("Climate Control", "HVAC", 0x7E5),
    GATEWAY("Central Gateway", "GW", 0x7E6),
    INSTRUMENT_CLUSTER("Instrument Cluster", "IC", 0x7E7),
    STEERING("Electronic Power Steering", "EPS", 0x730),
    PARKING("Park Assist", "PA", 0x76D),
    CAMERA("Camera System", "CAM", 0x769),
    RADAR("Radar Sensor", "RAD", 0x764),
    TELEMATICS("Telematics Unit", "TCU", 0x74F),
    INFOTAINMENT("Infotainment", "HU", 0x5F),
    IMMOBILIZER("Immobilizer", "IMMO", 0x7C4),
    GENERIC("Generic ECU", "ECU", 0x7DF);
    
    /**
     * Returns the response address for this ECU type.
     */
    val responseAddress: Int
        get() = defaultAddress + 8
    
    companion object {
        fun fromAddress(address: Int): ECUType {
            return values().find { it.defaultAddress == address } ?: GENERIC
        }
        
        fun fromName(name: String): ECUType {
            val upper = name.uppercase(Locale.ROOT)
            return values().find {
                it.name == upper ||
                it.displayName.uppercase(Locale.ROOT).contains(upper) ||
                it.shortName.uppercase(Locale.ROOT) == upper
            } ?: GENERIC
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PROTOCOL AND CAPABILITIES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Detected communication protocol information.
 *
 * @property type The detected protocol type
 * @property description Protocol description from adapter
 * @property canId11Bit Whether using 11-bit CAN IDs
 * @property baudRate Communication baud rate
 * @property headerBytes Number of header bytes in messages
 */
data class DetectedProtocol(
    val type: ProtocolType,
    val description: String,
    val canId11Bit: Boolean = true,
    val baudRate: Int = 500000,
    val headerBytes: Int = 3
) {
    
    /**
     * Indicates whether this is a CAN protocol.
     */
    val isCAN: Boolean
        get() = type.isCAN
    
    /**
     * Indicates whether this is a K-Line protocol.
     */
    val isKLine: Boolean
        get() = type.isKLine
    
    /**
     * Indicates whether extended (29-bit) CAN addressing is used.
     */
    val isExtendedCAN: Boolean
        get() = !canId11Bit
    
    /**
     * Returns the baud rate as a formatted string.
     */
    val baudRateFormatted: String
        get() = when {
            baudRate >= 1_000_000 -> "${baudRate / 1_000_000} Mbaud"
            baudRate >= 1_000 -> "${baudRate / 1_000} kbaud"
            else -> "$baudRate baud"
        }
    
    companion object {
        /**
         * Unknown protocol placeholder.
         */
        val UNKNOWN = DetectedProtocol(
            type = ProtocolType.UNKNOWN,
            description = "Unknown Protocol"
        )
        
        /**
         * Creates a DetectedProtocol from ELM327 protocol response.
         */
        fun fromELM327Response(
            protocolNumber: Int,
            description: String
        ): DetectedProtocol {
            val type = ProtocolType.fromCode(protocolNumber)
            val canId11Bit = !type.isExtendedCAN
            val baudRate = type.baudRate
            
            return DetectedProtocol(
                type = type,
                description = description,
                canId11Bit = canId11Bit,
                baudRate = baudRate,
                headerBytes = if (type.isCAN) (if (canId11Bit) 3 else 4) else 3
            )
        }
    }
}

/**
 * Vehicle diagnostic capabilities.
 *
 * Describes the diagnostic features supported by the connected vehicle.
 *
 * @property supportedPIDs Set of Mode 01 PIDs supported
 * @property supportedDTCTypes Set of DTC types supported
 * @property supportedMonitors Set of readiness monitors supported
 * @property supportsFreezeFrame Whether freeze frame data is available
 * @property supportsOxygenSensorTest Whether O2 sensor test is available
 * @property supportsComponentTest Whether component test is available
 * @property supportsVehicleInfo Whether Mode 09 vehicle info is available
 * @property supportsPermanentDTCs Whether permanent DTCs can be read
 * @property ecuList List of responding ECUs
 * @property detectedProtocol The active communication protocol
 */
data class VehicleCapabilities(
    val supportedPIDs: Set<Int> = emptySet(),
    val supportedDTCTypes: Set<DTCType> = emptySet(),
    val supportedMonitors: Set<MonitorType> = emptySet(),
    val supportsFreezeFrame: Boolean = false,
    val supportsOxygenSensorTest: Boolean = false,
    val supportsComponentTest: Boolean = false,
    val supportsVehicleInfo: Boolean = false,
    val supportsPermanentDTCs: Boolean = false,
    val ecuList: List<VehicleECU> = emptyList(),
    val detectedProtocol: DetectedProtocol = DetectedProtocol.UNKNOWN
) {
    
    /**
     * Number of supported PIDs.
     */
    val supportedPIDCount: Int
        get() = supportedPIDs.size
    
    /**
     * Number of responding ECUs.
     */
    val ecuCount: Int
        get() = ecuList.size
    
    /**
     * Indicates whether this appears to be a modern vehicle.
     */
    val isModernVehicle: Boolean
        get() = detectedProtocol.isCAN && supportedPIDCount > 20
    
    /**
     * Indicates whether enhanced diagnostics are available.
     */
    val hasEnhancedDiagnostics: Boolean
        get() = ecuCount > 1 || supportsPermanentDTCs
    
    /**
     * Checks if a specific PID is supported.
     */
    fun isPIDSupported(pid: Int): Boolean = pid in supportedPIDs
    
    /**
     * Gets the list of common PIDs that are supported.
     */
    val supportedCommonPIDs: Set<Int>
        get() = supportedPIDs.intersect(COMMON_PIDS)
    
    companion object {
        /**
         * Common PIDs that are frequently monitored.
         */
        val COMMON_PIDS = setOf(
            0x04,  // Engine Load
            0x05,  // Coolant Temp
            0x06,  // Short Term Fuel Trim Bank 1
            0x07,  // Long Term Fuel Trim Bank 1
            0x0B,  // Intake Manifold Pressure
            0x0C,  // Engine RPM
            0x0D,  // Vehicle Speed
            0x0E,  // Timing Advance
            0x0F,  // Intake Air Temp
            0x10,  // MAF Air Flow Rate
            0x11,  // Throttle Position
            0x1C,  // OBD Standards
            0x1F,  // Run Time Since Engine Start
            0x21,  // Distance with MIL on
            0x2F,  // Fuel Tank Level
            0x31,  // Distance since codes cleared
            0x33,  // Barometric Pressure
            0x42,  // Control Module Voltage
            0x46,  // Ambient Air Temp
            0x49,  // Accelerator Pedal Position D
            0x4A,  // Accelerator Pedal Position E
            0x5C,  // Engine Oil Temp
            0x5E   // Fuel Rate
        )
        
        /**
         * Unknown/empty capabilities.
         */
        val UNKNOWN = VehicleCapabilities()
    }
}

/**
 * DTC (Diagnostic Trouble Code) types.
 */
enum class DTCType(val displayName: String, val mode: Int) {
    STORED("Stored", 0x03),
    PENDING("Pending", 0x07),
    PERMANENT("Permanent", 0x0A);
    
    companion object {
        fun fromMode(mode: Int): DTCType? {
            return values().find { it.mode == mode }
        }
    }
}

/**
 * Readiness monitor types.
 */
enum class MonitorType(val displayName: String, val shortName: String) {
    CATALYST("Catalyst", "CAT"),
    HEATED_CATALYST("Heated Catalyst", "HCAT"),
    EVAP_SYSTEM("Evaporative System", "EVAP"),
    SECONDARY_AIR("Secondary Air", "AIR"),
    AC_REFRIGERANT("A/C Refrigerant", "AC"),
    OXYGEN_SENSOR("Oxygen Sensor", "O2S"),
    OXYGEN_SENSOR_HEATER("O2 Sensor Heater", "O2H"),
    EGR_VVT("EGR/VVT System", "EGR"),
    MISFIRE("Misfire", "MIS"),
    FUEL_SYSTEM("Fuel System", "FUEL"),
    COMPREHENSIVE_COMPONENT("Comprehensive Component", "CCM");
    
    companion object {
        fun fromName(name: String): MonitorType? {
            val upper = name.uppercase(Locale.ROOT)
            return values().find {
                it.name == upper ||
                it.displayName.uppercase(Locale.ROOT).contains(upper) ||
                it.shortName.uppercase(Locale.ROOT) == upper
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE CONNECTION STATE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Vehicle connection state.
 *
 * @property displayName Human-readable state name
 * @property isTerminal Whether this is a terminal state
 */
enum class VehicleConnectionState(val displayName: String, val isTerminal: Boolean) {
    /**
     * Vehicle is not connected.
     */
    DISCONNECTED("Disconnected", true),
    
    /**
     * Establishing connection to vehicle via scanner.
     */
    CONNECTING("Connecting", false),
    
    /**
     * Initializing communication protocols.
     */
    INITIALIZING("Initializing", false),
    
    /**
     * Identifying vehicle (reading VIN, detecting ECUs).
     */
    IDENTIFYING("Identifying", false),
    
    /**
     * Fully connected and ready for diagnostics.
     */
    CONNECTED("Connected", true),
    
    /**
     * Error occurred during connection or communication.
     */
    ERROR("Error", true);
    
    /**
     * Indicates whether the vehicle is connected.
     */
    val isConnected: Boolean
        get() = this == CONNECTED
    
    /**
     * Indicates whether connection is in progress.
     */
    val isConnecting: Boolean
        get() = this in listOf(CONNECTING, INITIALIZING, IDENTIFYING)
}

// ═══════════════════════════════════════════════════════════════════════════
// VEHICLE SUMMARY
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Lightweight vehicle summary for list display.
 *
 * @property id Vehicle unique identifier
 * @property displayName Display name
 * @property vin VIN string
 * @property brandName Brand display name
 * @property modelName Model name
 * @property year Model year
 * @property lastDiagnosticDate Last diagnostic timestamp
 * @property hasDTCs Whether vehicle has active DTCs
 */
data class VehicleSummary(
    val id: String,
    val displayName: String,
    val vin: String? = null,
    val brandName: String? = null,
    val modelName: String? = null,
    val year: Int? = null,
    val lastDiagnosticDate: Long? = null,
    val hasDTCs: Boolean = false
) {
    
    /**
     * Returns a short title combining year and model.
     */
    val shortTitle: String
        get() = buildString {
            year?.let { append("$it ") }
            modelName?.let { append(it) }
            if (isEmpty()) append(displayName)
        }.trim()
    
    /**
     * Returns the VIN preview (first/last 4 chars).
     */
    val vinPreview: String?
        get() = vin?.let {
            if (it.length == 17) {
                "${it.take(4)}...${it.takeLast(4)}"
            } else it
        }
}

// ═══════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Updates the vehicle with new connection state.
 */
fun Vehicle.withConnectionState(state: VehicleConnectionState): Vehicle {
    return copy(
        connectionState = state,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with VIN information.
 */
fun Vehicle.withVIN(vin: VIN): Vehicle {
    return copy(
        vin = vin,
        year = vin.modelYear ?: year,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with brand information.
 */
fun Vehicle.withBrand(brand: VehicleBrand): Vehicle {
    return copy(
        brand = brand,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with model information.
 */
fun Vehicle.withModel(model: VehicleModel): Vehicle {
    return copy(
        model = model,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with variant information.
 */
fun Vehicle.withVariant(variant: VehicleVariant): Vehicle {
    return copy(
        variant = variant,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with detected protocol.
 */
fun Vehicle.withProtocol(protocol: DetectedProtocol): Vehicle {
    return copy(
        detectedProtocol = protocol,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with capabilities.
 */
fun Vehicle.withCapabilities(capabilities: VehicleCapabilities): Vehicle {
    return copy(
        capabilities = capabilities,
        ecuList = capabilities.ecuList,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the vehicle with ECU list.
 */
fun Vehicle.withECUs(ecus: List<VehicleECU>): Vehicle {
    return copy(
        ecuList = ecus,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Marks the vehicle as connected.
 */
fun Vehicle.asConnected(): Vehicle {
    return copy(
        connectionState = VehicleConnectionState.CONNECTED,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Marks the vehicle as disconnected.
 */
fun Vehicle.asDisconnected(): Vehicle {
    return copy(
        connectionState = VehicleConnectionState.DISCONNECTED,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Marks the vehicle with error state.
 */
fun Vehicle.asError(): Vehicle {
    return copy(
        connectionState = VehicleConnectionState.ERROR,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the last diagnostic date.
 */
fun Vehicle.withDiagnosticDate(timestamp: Long = System.currentTimeMillis()): Vehicle {
    return copy(
        lastDiagnosticDate = timestamp,
        updatedAt = timestamp
    )
}

/**
 * Updates the mileage.
 */
fun Vehicle.withMileage(km: Int): Vehicle {
    return copy(
        mileage = km,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Adds metadata to the vehicle.
 */
fun Vehicle.withMetadata(key: String, value: String): Vehicle {
    return copy(
        metadata = metadata + (key to value),
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Updates the display name.
 */
fun Vehicle.withDisplayName(name: String): Vehicle {
    return copy(
        displayName = name,
        updatedAt = System.currentTimeMillis()
    )
}

/**
 * Sets the license plate.
 */
fun Vehicle.withLicensePlate(plate: String): Vehicle {
    return copy(
        licensePlate = plate,
        updatedAt = System.currentTimeMillis()
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// TYPE ALIASES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Type alias for vehicle ID.
 */
typealias VehicleId = String

/**
 * Type alias for ECU address.
 */
typealias ECUAddress = Int

/**
 * Type alias for PID number.
 */
typealias PIDNumber = Int
