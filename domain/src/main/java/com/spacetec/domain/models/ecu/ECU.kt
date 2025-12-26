/**
 * File: ECU.kt
 * 
 * Domain model representing an Electronic Control Unit (ECU) in a vehicle.
 * ECUs are the computer modules that control various vehicle systems such as
 * engine management, transmission, ABS, airbags, and more.
 * 
 * This file contains the complete ECU domain model with identification,
 * capabilities, state tracking, and supporting enums and data classes.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. ECU data class with full documentation
 * 3. ECUType enum with all ECU categories
 * 4. ECUCapabilities data class
 * 5. ECUState data class
 * 6. CommunicationStatus enum
 * 7. Supporting data classes and extension functions
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.domain.models.ecu

import com.spacetec.obd.core.domain.models.scanner.ProtocolType
import java.io.Serializable
import java.util.Locale

/**
 * Represents an Electronic Control Unit (ECU) in a vehicle.
 * 
 * ECUs are embedded systems that control one or more electrical systems
 * or subsystems in a vehicle. Modern vehicles can have 50-100+ ECUs
 * controlling everything from engine management to infotainment.
 * 
 * This model captures:
 * - **Identification**: Address, type, manufacturer info
 * - **Version Information**: Hardware/software versions, calibration IDs
 * - **Capabilities**: Supported services and features
 * - **State**: Current communication status, session type, security level
 * 
 * ## ECU Addressing
 * 
 * ECUs are addressed differently depending on the protocol:
 * - **CAN (ISO 15765-4)**: 11-bit or 29-bit CAN IDs (e.g., 0x7E0-0x7E7)
 * - **KWP2000/ISO 9141**: Physical addresses (e.g., 0x01-0xFF)
 * - **J1850**: 1-byte addresses
 * 
 * ## Standard OBD-II ECU Addresses (CAN)
 * 
 * | Address | Response | Typical ECU |
 * |---------|----------|-------------|
 * | 0x7E0   | 0x7E8    | Engine (ECM) |
 * | 0x7E1   | 0x7E9    | Transmission (TCM) |
 * | 0x7E2   | 0x7EA    | ABS/Brakes |
 * | 0x7E3   | 0x7EB    | Airbag (SRS) |
 * | 0x7E4   | 0x7EC    | Body (BCM) |
 * | 0x7E5   | 0x7ED    | Climate (HVAC) |
 * | 0x7E6   | 0x7EE    | Various |
 * | 0x7E7   | 0x7EF    | Various |
 * | 0x7DF   | -        | Broadcast (all ECUs) |
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * val engineECU = ECU(
 *     address = 0x7E0,
 *     type = ECUType.ENGINE,
 *     name = "Engine Control Module",
 *     manufacturer = "Bosch",
 *     softwareVersion = "1.2.3"
 * )
 * 
 * if (engineECU.isReachable) {
 *     println("ECU ${engineECU.name} at ${engineECU.addressHex}")
 * }
 * ```
 * 
 * @property address Unique ECU address on the diagnostic bus
 * @property type ECU type/category
 * @property name Human-readable ECU name
 * @property shortName Short name/abbreviation (e.g., "ECM", "TCM")
 * @property manufacturer ECU manufacturer (e.g., "Bosch", "Denso", "Continental")
 * @property hardwarePartNumber Hardware part number
 * @property hardwareVersion Hardware version string
 * @property softwarePartNumber Software part number
 * @property softwareVersion Software version string
 * @property bootSoftwareId Boot software identification
 * @property applicationSoftwareId Application software identification
 * @property applicationDataId Application data identification
 * @property serialNumber ECU serial number
 * @property manufacturingDate Manufacturing date (format varies by manufacturer)
 * @property programmingDate Last programming date
 * @property calibrationIds Calibration identifiers (CVN)
 * @property capabilities ECU capabilities and supported features
 * @property supportedServices Set of supported diagnostic service IDs
 * @property supportedDIDs Set of supported Data Identifiers
 * @property state Current ECU runtime state
 * @property protocol Protocol used to communicate with this ECU
 * @property responseAddress Response address for CAN (usually address + 8)
 * @property vin VIN stored in this ECU (if available)
 * @property discoveredAt Timestamp when ECU was first discovered
 * @property lastCommunicationAt Timestamp of last successful communication
 * @property rawIdentification Raw identification response bytes
 * 
 * @see ECUType
 * @see ECUCapabilities
 * @see ECUState
 */
data class ECU(
    val address: Int,
    val type: ECUType,
    val name: String,
    val shortName: String? = null,
    val manufacturer: String? = null,
    val hardwarePartNumber: String? = null,
    val hardwareVersion: String? = null,
    val softwarePartNumber: String? = null,
    val softwareVersion: String? = null,
    val bootSoftwareId: String? = null,
    val applicationSoftwareId: String? = null,
    val applicationDataId: String? = null,
    val serialNumber: String? = null,
    val manufacturingDate: String? = null,
    val programmingDate: String? = null,
    val calibrationIds: List<String> = emptyList(),
    val capabilities: ECUCapabilities = ECUCapabilities(),
    val supportedServices: Set<Int> = emptySet(),
    val supportedDIDs: Set<Int> = emptySet(),
    val state: ECUState = ECUState(),
    val protocol: ProtocolType? = null,
    val responseAddress: Int? = null,
    val vin: String? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
    val lastCommunicationAt: Long? = null,
    val rawIdentification: ByteArray? = null
) : Serializable {

    // ==================== Computed Properties ====================

    /**
     * Full ECU identifier string combining type prefix and address.
     * 
     * Format: `{TYPE_PREFIX}_{ADDRESS_HEX}`
     * Example: `ECM_7E0`, `TCM_7E1`, `ABS_7E2`
     */
    val identifier: String
        get() = "${type.prefix}_${address.toString(16).uppercase(Locale.ROOT).padStart(3, '0')}"

    /**
     * ECU address formatted as hexadecimal string.
     * 
     * Example: `0x7E0`
     */
    val addressHex: String
        get() = "0x${address.toString(16).uppercase(Locale.ROOT).padStart(3, '0')}"

    /**
     * Response address formatted as hexadecimal string.
     * 
     * For CAN, typically address + 8.
     */
    val responseAddressHex: String?
        get() = responseAddress?.let { 
            "0x${it.toString(16).uppercase(Locale.ROOT).padStart(3, '0')}" 
        }

    /**
     * Whether the ECU is currently reachable for communication.
     */
    val isReachable: Boolean
        get() = state.communicationStatus == CommunicationStatus.OK

    /**
     * Whether the ECU has any DTCs stored.
     */
    val hasDTCs: Boolean
        get() = state.dtcCount > 0

    /**
     * Whether security access has been granted.
     */
    val isSecurityUnlocked: Boolean
        get() = state.securityLevel != null && state.securityLevel > 0

    /**
     * Whether the MIL (check engine light) is illuminated.
     */
    val isMILOn: Boolean
        get() = state.milStatus

    /**
     * Whether this is a powertrain ECU (engine or transmission).
     */
    val isPowertrainECU: Boolean
        get() = type in listOf(ECUType.ENGINE, ECUType.POWERTRAIN, ECUType.TRANSMISSION)

    /**
     * Whether this is a safety-related ECU.
     */
    val isSafetyECU: Boolean
        get() = type in listOf(ECUType.AIRBAG, ECUType.ABS, ECUType.STEERING)

    /**
     * Display name combining short name and full name.
     * 
     * Example: "ECM - Engine Control Module"
     */
    val displayName: String
        get() = shortName?.let { "$it - $name" } ?: name

    /**
     * Software identification string combining version info.
     */
    val softwareId: String?
        get() = listOfNotNull(
            softwarePartNumber,
            softwareVersion
        ).takeIf { it.isNotEmpty() }?.joinToString(" ")

    /**
     * Hardware identification string combining version info.
     */
    val hardwareId: String?
        get() = listOfNotNull(
            hardwarePartNumber,
            hardwareVersion
        ).takeIf { it.isNotEmpty() }?.joinToString(" ")

    /**
     * Whether this ECU has complete identification information.
     */
    val hasCompleteIdentification: Boolean
        get() = manufacturer != null && 
                (softwareVersion != null || hardwareVersion != null) &&
                (softwarePartNumber != null || hardwarePartNumber != null)

    /**
     * Time since last communication in milliseconds.
     */
    val timeSinceLastCommunication: Long?
        get() = lastCommunicationAt?.let { System.currentTimeMillis() - it }

    // ==================== Methods ====================

    /**
     * Checks if this ECU supports a specific diagnostic service.
     * 
     * @param service OBD-II/UDS service ID (e.g., 0x01 for Mode 01, 0x22 for Read DID)
     * @return true if service is supported
     */
    fun supports(service: Int): Boolean = service in supportedServices

    /**
     * Checks if this ECU supports a specific Data Identifier.
     * 
     * @param did Data Identifier (e.g., 0xF190 for VIN)
     * @return true if DID is supported
     */
    fun supportsDID(did: Int): Boolean = did in supportedDIDs

    /**
     * Checks if this ECU supports a specific capability.
     * 
     * @param capability Capability to check
     * @return true if capability is supported
     */
    fun hasCapability(capability: ECUCapability): Boolean = when (capability) {
        ECUCapability.READ_DTC -> capabilities.supportsDTCRead
        ECUCapability.CLEAR_DTC -> capabilities.supportsDTCClear
        ECUCapability.READ_FREEZE_FRAME -> capabilities.supportsFreezeFrame
        ECUCapability.READ_LIVE_DATA -> capabilities.supportsLiveData
        ECUCapability.SECURITY_ACCESS -> capabilities.supportsSecurityAccess
        ECUCapability.ROUTINE_CONTROL -> capabilities.supportsRoutineControl
        ECUCapability.IO_CONTROL -> capabilities.supportsIOControl
        ECUCapability.CODING -> capabilities.supportsCoding
        ECUCapability.PROGRAMMING -> capabilities.supportsProgramming
    }

    /**
     * Creates a copy with updated state.
     */
    fun withState(newState: ECUState): ECU = copy(
        state = newState,
        lastCommunicationAt = System.currentTimeMillis()
    )

    /**
     * Creates a copy with updated communication status.
     */
    fun withCommunicationStatus(status: CommunicationStatus): ECU = copy(
        state = state.copy(communicationStatus = status),
        lastCommunicationAt = if (status == CommunicationStatus.OK) {
            System.currentTimeMillis()
        } else {
            lastCommunicationAt
        }
    )

    /**
     * Creates a copy with updated DTC information.
     */
    fun withDTCInfo(count: Int, milOn: Boolean): ECU = copy(
        state = state.copy(dtcCount = count, milStatus = milOn)
    )

    /**
     * Creates a copy with added supported services.
     */
    fun withSupportedServices(services: Set<Int>): ECU = copy(
        supportedServices = supportedServices + services
    )

    /**
     * Creates a copy with added supported DIDs.
     */
    fun withSupportedDIDs(dids: Set<Int>): ECU = copy(
        supportedDIDs = supportedDIDs + dids
    )

    /**
     * Gets a summary string for display.
     */
    fun getSummary(): String = buildString {
        appendLine("$displayName ($addressHex)")
        manufacturer?.let { appendLine("Manufacturer: $it") }
        softwareId?.let { appendLine("Software: $it") }
        hardwareId?.let { appendLine("Hardware: $it") }
        if (hasDTCs) appendLine("DTCs: ${state.dtcCount}")
        appendLine("Status: ${state.communicationStatus}")
    }

    // ==================== Equals, HashCode, ToString ====================

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ECU

        if (address != other.address) return false
        if (type != other.type) return false
        if (name != other.name) return false
        if (softwareVersion != other.softwareVersion) return false
        if (hardwareVersion != other.hardwareVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address
        result = 31 * result + type.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (softwareVersion?.hashCode() ?: 0)
        result = 31 * result + (hardwareVersion?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = buildString {
        append("ECU(")
        append("address=$addressHex, ")
        append("type=$type, ")
        append("name='$name'")
        manufacturer?.let { append(", manufacturer='$it'") }
        softwareVersion?.let { append(", sw='$it'") }
        append(", status=${state.communicationStatus}")
        append(")")
    }

    // ==================== Companion Object ====================

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Standard OBD-II ECU request addresses for CAN.
         */
        val STANDARD_ADDRESSES = listOf(
            0x7E0, 0x7E1, 0x7E2, 0x7E3,
            0x7E4, 0x7E5, 0x7E6, 0x7E7
        )

        /**
         * Standard OBD-II ECU response addresses for CAN.
         */
        val STANDARD_RESPONSE_ADDRESSES = listOf(
            0x7E8, 0x7E9, 0x7EA, 0x7EB,
            0x7EC, 0x7ED, 0x7EE, 0x7EF
        )

        /**
         * OBD-II broadcast/functional address.
         */
        const val BROADCAST_ADDRESS = 0x7DF

        /**
         * 29-bit CAN broadcast address.
         */
        const val BROADCAST_ADDRESS_29BIT = 0x18DB33F1

        /**
         * Creates an ECU from a scan/discovery response.
         * 
         * @param address ECU address
         * @param response Raw response bytes from ECU
         * @param protocol Protocol used for communication
         * @return Constructed ECU with basic information
         */
        fun fromScanResponse(
            address: Int,
            response: ByteArray,
            protocol: ProtocolType? = null
        ): ECU {
            val type = ECUType.fromAddress(address)
            val responseAddr = if (address in STANDARD_ADDRESSES) {
                address + 8
            } else {
                null
            }

            return ECU(
                address = address,
                type = type,
                name = type.description,
                shortName = type.prefix,
                protocol = protocol,
                responseAddress = responseAddr,
                rawIdentification = response,
                state = ECUState(communicationStatus = CommunicationStatus.OK),
                discoveredAt = System.currentTimeMillis(),
                lastCommunicationAt = System.currentTimeMillis()
            )
        }

        /**
         * Creates an ECU from UDS Read ECU Identification response.
         * 
         * @param address ECU address
         * @param identificationData Map of DID to value
         * @return ECU with identification information
         */
        fun fromIdentification(
            address: Int,
            identificationData: Map<Int, String>
        ): ECU {
            return ECU(
                address = address,
                type = ECUType.fromAddress(address),
                name = identificationData[DID_ECU_NAME] 
                    ?: ECUType.fromAddress(address).description,
                shortName = ECUType.fromAddress(address).prefix,
                manufacturer = identificationData[DID_MANUFACTURER],
                hardwarePartNumber = identificationData[DID_HARDWARE_PART_NUMBER],
                hardwareVersion = identificationData[DID_HARDWARE_VERSION],
                softwarePartNumber = identificationData[DID_SOFTWARE_PART_NUMBER],
                softwareVersion = identificationData[DID_SOFTWARE_VERSION],
                serialNumber = identificationData[DID_SERIAL_NUMBER],
                vin = identificationData[DID_VIN],
                manufacturingDate = identificationData[DID_MANUFACTURING_DATE],
                programmingDate = identificationData[DID_PROGRAMMING_DATE]
            )
        }

        /**
         * Creates an unknown ECU with minimal information.
         */
        fun unknown(address: Int): ECU = ECU(
            address = address,
            type = ECUType.UNKNOWN,
            name = "Unknown ECU at 0x${address.toString(16).uppercase()}"
        )

        // Common UDS Data Identifiers for ECU Identification
        const val DID_VIN = 0xF190
        const val DID_ECU_NAME = 0xF197
        const val DID_MANUFACTURER = 0xF18A
        const val DID_HARDWARE_PART_NUMBER = 0xF191
        const val DID_HARDWARE_VERSION = 0xF193
        const val DID_SOFTWARE_PART_NUMBER = 0xF194
        const val DID_SOFTWARE_VERSION = 0xF195
        const val DID_SERIAL_NUMBER = 0xF18C
        const val DID_MANUFACTURING_DATE = 0xF18B
        const val DID_PROGRAMMING_DATE = 0xF199
        const val DID_BOOT_SOFTWARE_ID = 0xF180
        const val DID_APPLICATION_SOFTWARE_ID = 0xF181
        const val DID_APPLICATION_DATA_ID = 0xF182
        const val DID_CALIBRATION_ID = 0xF806
    }
}

// ==================== ECU Type ====================

/**
 * Enumeration of ECU types/categories.
 * 
 * Each ECU type has a standard prefix used in identifiers and an optional
 * default CAN address for OBD-II communication.
 * 
 * @property prefix Short prefix for identification (e.g., "ECM", "TCM")
 * @property description Human-readable description
 * @property defaultAddress Default CAN address (null if varies)
 */
enum class ECUType(
    val prefix: String,
    val description: String,
    val defaultAddress: Int?
) {
    /** Engine Control Module / Engine Control Unit */
    ENGINE("ECM", "Engine Control Module", 0x7E0),
    
    /** Powertrain Control Module (combined engine + transmission) */
    POWERTRAIN("PCM", "Powertrain Control Module", 0x7E0),
    
    /** Transmission Control Module */
    TRANSMISSION("TCM", "Transmission Control Module", 0x7E1),
    
    /** Anti-lock Braking System */
    ABS("ABS", "Anti-lock Braking System", 0x7E2),
    
    /** Supplemental Restraint System (Airbags) */
    AIRBAG("SRS", "Supplemental Restraint System", 0x7E3),
    
    /** Body Control Module */
    BODY("BCM", "Body Control Module", 0x7E4),
    
    /** Instrument Panel Cluster */
    INSTRUMENT("IPC", "Instrument Panel Cluster", 0x7E5),
    
    /** Heating, Ventilation, and Air Conditioning */
    HVAC("HVAC", "Climate Control Module", 0x7E6),
    
    /** Electric Power Steering */
    STEERING("EPS", "Electric Power Steering", null),
    
    /** Electronic Stability Control */
    STABILITY("ESC", "Electronic Stability Control", null),
    
    /** Suspension Control Module */
    SUSPENSION("SUSP", "Suspension Control Module", null),
    
    /** Park Assist Module */
    PARK_ASSIST("PAM", "Park Assist Module", null),
    
    /** Gateway Module */
    GATEWAY("GW", "Gateway Module", null),
    
    /** Central Electronics Module */
    CENTRAL("CEM", "Central Electronics Module", null),
    
    /** Telematics Control Unit */
    TELEMATICS("TCU", "Telematics Control Unit", null),
    
    /** In-Vehicle Infotainment */
    INFOTAINMENT("IVI", "Infotainment System", null),
    
    /** Audio/Radio Control */
    AUDIO("AUD", "Audio System", null),
    
    /** Navigation System */
    NAVIGATION("NAV", "Navigation System", null),
    
    /** Battery Management System (Hybrid/EV) */
    BATTERY("BMS", "Battery Management System", null),
    
    /** On-Board Charger (EV) */
    CHARGER("OBC", "On-Board Charger", null),
    
    /** Hybrid Control Module */
    HYBRID("HCM", "Hybrid Control Module", null),
    
    /** Electric Motor Control */
    MOTOR("MCU", "Motor Control Unit", null),
    
    /** DC-DC Converter */
    DCDC("DCDC", "DC-DC Converter", null),
    
    /** Fuel Cell Control (FCEV) */
    FUEL_CELL("FCCM", "Fuel Cell Control Module", null),
    
    /** Adaptive Cruise Control */
    ACC("ACC", "Adaptive Cruise Control", null),
    
    /** Lane Departure Warning */
    LDW("LDW", "Lane Departure Warning", null),
    
    /** Blind Spot Detection */
    BSD("BSD", "Blind Spot Detection", null),
    
    /** Forward Collision Warning */
    FCW("FCW", "Forward Collision Warning", null),
    
    /** Automatic Emergency Braking */
    AEB("AEB", "Automatic Emergency Braking", null),
    
    /** Tire Pressure Monitoring */
    TPMS("TPMS", "Tire Pressure Monitoring", null),
    
    /** Keyless Entry/Start */
    KEYLESS("PEPS", "Passive Entry Passive Start", null),
    
    /** Door Control Module (Driver) */
    DOOR_DRIVER("DDM", "Driver Door Module", null),
    
    /** Door Control Module (Passenger) */
    DOOR_PASSENGER("PDM", "Passenger Door Module", null),
    
    /** Seat Control Module */
    SEAT("SCM", "Seat Control Module", null),
    
    /** Sunroof/Moonroof Control */
    SUNROOF("SRM", "Sunroof Module", null),
    
    /** Lighting Control Module */
    LIGHTING("LCM", "Lighting Control Module", null),
    
    /** Headlamp Control */
    HEADLAMP("HCM", "Headlamp Control Module", null),
    
    /** Wiper Control Module */
    WIPER("WCM", "Wiper Control Module", null),
    
    /** Rain/Light Sensor */
    RAIN_SENSOR("RLS", "Rain/Light Sensor", null),
    
    /** Trailer Module */
    TRAILER("TRM", "Trailer Module", null),
    
    /** Transfer Case Control */
    TRANSFER_CASE("TCCM", "Transfer Case Control", null),
    
    /** Four-Wheel Drive Module */
    FOUR_WHEEL_DRIVE("4WD", "Four-Wheel Drive Module", null),
    
    /** Exhaust/Emissions Control */
    EMISSIONS("ECM", "Emissions Control Module", null),
    
    /** Diesel Particulate Filter */
    DPF("DPF", "Diesel Particulate Filter", null),
    
    /** Selective Catalytic Reduction */
    SCR("SCR", "SCR Control Module", null),
    
    /** Fuel Pump Control */
    FUEL_PUMP("FPC", "Fuel Pump Control", null),
    
    /** Unknown/Unidentified ECU */
    UNKNOWN("UNK", "Unknown ECU", null);

    /**
     * Whether this ECU type is safety-critical.
     */
    val isSafetyCritical: Boolean
        get() = this in listOf(
            AIRBAG, ABS, STABILITY, STEERING,
            AEB, FCW, ACC
        )

    /**
     * Whether this ECU type is powertrain-related.
     */
    val isPowertrain: Boolean
        get() = this in listOf(
            ENGINE, POWERTRAIN, TRANSMISSION,
            HYBRID, MOTOR, FUEL_CELL, BATTERY
        )

    /**
     * Whether this ECU type is ADAS (Advanced Driver Assistance).
     */
    val isADAS: Boolean
        get() = this in listOf(
            ACC, LDW, BSD, FCW, AEB, PARK_ASSIST
        )

    /**
     * Whether this ECU type is related to electric/hybrid vehicles.
     */
    val isElectric: Boolean
        get() = this in listOf(
            BATTERY, CHARGER, HYBRID, MOTOR, DCDC, FUEL_CELL
        )

    companion object {
        /**
         * Gets ECU type from CAN address.
         * 
         * @param address CAN address
         * @return Matching ECU type or UNKNOWN
         */
        fun fromAddress(address: Int): ECUType {
            return values().find { it.defaultAddress == address } ?: UNKNOWN
        }

        /**
         * Gets ECU type from name/prefix.
         * 
         * @param name ECU name or prefix
         * @return Matching ECU type or UNKNOWN
         */
        fun fromName(name: String): ECUType {
            val upperName = name.uppercase(Locale.ROOT).trim()
            
            // Check prefix match
            values().find { it.prefix == upperName }?.let { return it }
            
            // Check description match
            values().find { 
                it.description.uppercase(Locale.ROOT).contains(upperName) ||
                upperName.contains(it.prefix)
            }?.let { return it }
            
            // Check common aliases
            return when {
                upperName.contains("ENGINE") || upperName == "ECU" -> ENGINE
                upperName.contains("TRANS") || upperName == "TCU" -> TRANSMISSION
                upperName.contains("BRAKE") -> ABS
                upperName.contains("AIRBAG") || upperName == "ACM" -> AIRBAG
                upperName.contains("BODY") -> BODY
                upperName.contains("CLUSTER") || upperName.contains("DASH") -> INSTRUMENT
                upperName.contains("CLIMATE") || upperName.contains("AC") -> HVAC
                upperName.contains("STEER") -> STEERING
                upperName.contains("GATEWAY") -> GATEWAY
                upperName.contains("BATTERY") || upperName == "BMU" -> BATTERY
                else -> UNKNOWN
            }
        }

        /**
         * Gets all powertrain ECU types.
         */
        val POWERTRAIN_TYPES: List<ECUType>
            get() = values().filter { it.isPowertrain }

        /**
         * Gets all safety-critical ECU types.
         */
        val SAFETY_TYPES: List<ECUType>
            get() = values().filter { it.isSafetyCritical }

        /**
         * Gets all ADAS ECU types.
         */
        val ADAS_TYPES: List<ECUType>
            get() = values().filter { it.isADAS }
    }
}

// ==================== ECU Capabilities ====================

/**
 * Describes the diagnostic capabilities of an ECU.
 * 
 * @property supportsOBD Whether standard OBD-II services are supported
 * @property supportsUDS Whether UDS (ISO 14229) services are supported
 * @property supportsKWP Whether KWP2000 services are supported
 * @property supportsDTCRead Whether reading DTCs is supported
 * @property supportsDTCClear Whether clearing DTCs is supported
 * @property supportsFreezeFrame Whether reading freeze frames is supported
 * @property supportsLiveData Whether reading live data is supported
 * @property supportsSecurityAccess Whether security access is supported
 * @property supportsCoding Whether variant coding is supported
 * @property supportsRoutineControl Whether routine control is supported
 * @property supportsIOControl Whether I/O control is supported
 * @property supportsProgramming Whether ECU programming is supported
 * @property maxDataLength Maximum data length per message
 */
data class ECUCapabilities(
    val supportsOBD: Boolean = true,
    val supportsUDS: Boolean = false,
    val supportsKWP: Boolean = false,
    val supportsDTCRead: Boolean = true,
    val supportsDTCClear: Boolean = true,
    val supportsFreezeFrame: Boolean = true,
    val supportsLiveData: Boolean = true,
    val supportsSecurityAccess: Boolean = false,
    val supportsCoding: Boolean = false,
    val supportsRoutineControl: Boolean = false,
    val supportsIOControl: Boolean = false,
    val supportsProgramming: Boolean = false,
    val maxDataLength: Int = 7,
    val supportedDTCTypes: Set<DTCReadType> = setOf(
        DTCReadType.ALL,
        DTCReadType.STORED,
        DTCReadType.PENDING
    ),
    val supportedSecurityLevels: Set<Int> = emptySet()
) : Serializable {

    /**
     * Whether this ECU has advanced diagnostic capabilities.
     */
    val hasAdvancedCapabilities: Boolean
        get() = supportsUDS || supportsCoding || supportsProgramming

    /**
     * Whether this ECU supports any write operations.
     */
    val supportsWriteOperations: Boolean
        get() = supportsDTCClear || supportsCoding || 
                supportsIOControl || supportsProgramming

    /**
     * Creates capabilities for a standard OBD-II ECU.
     */
    companion object {
        private const val serialVersionUID = 1L

        /**
         * Standard OBD-II capabilities.
         */
        val OBD_STANDARD = ECUCapabilities(
            supportsOBD = true,
            supportsUDS = false,
            supportsDTCRead = true,
            supportsDTCClear = true,
            supportsFreezeFrame = true,
            supportsLiveData = true,
            maxDataLength = 7
        )

        /**
         * Full UDS capabilities.
         */
        val UDS_FULL = ECUCapabilities(
            supportsOBD = true,
            supportsUDS = true,
            supportsDTCRead = true,
            supportsDTCClear = true,
            supportsFreezeFrame = true,
            supportsLiveData = true,
            supportsSecurityAccess = true,
            supportsCoding = true,
            supportsRoutineControl = true,
            supportsIOControl = true,
            supportsProgramming = true,
            maxDataLength = 4095,
            supportedSecurityLevels = setOf(0x01, 0x03, 0x11, 0x27)
        )

        /**
         * Limited capabilities for simple ECUs.
         */
        val LIMITED = ECUCapabilities(
            supportsOBD = true,
            supportsUDS = false,
            supportsDTCRead = true,
            supportsDTCClear = false,
            supportsFreezeFrame = false,
            supportsLiveData = false,
            maxDataLength = 7
        )
    }
}

/**
 * DTC read types.
 */
enum class DTCReadType {
    ALL,
    STORED,
    PENDING,
    PERMANENT,
    EMISSIONS_RELATED
}

/**
 * ECU capability flags for quick checking.
 */
enum class ECUCapability {
    READ_DTC,
    CLEAR_DTC,
    READ_FREEZE_FRAME,
    READ_LIVE_DATA,
    SECURITY_ACCESS,
    ROUTINE_CONTROL,
    IO_CONTROL,
    CODING,
    PROGRAMMING
}

// ==================== ECU State ====================

/**
 * Runtime state of an ECU.
 * 
 * Tracks the current communication status, session type, security level,
 * and other runtime information.
 * 
 * @property communicationStatus Current communication status
 * @property sessionType Active diagnostic session type
 * @property securityLevel Current security access level (null if locked)
 * @property dtcCount Number of DTCs stored
 * @property milStatus MIL (check engine light) status
 * @property lastError Last error message (if any)
 * @property lastResponseTime Last response time in milliseconds
 * @property consecutiveErrors Number of consecutive communication errors
 */
data class ECUState(
    val communicationStatus: CommunicationStatus = CommunicationStatus.UNKNOWN,
    val sessionType: SessionType? = null,
    val securityLevel: Int? = null,
    val dtcCount: Int = 0,
    val milStatus: Boolean = false,
    val lastError: String? = null,
    val lastResponseTime: Long? = null,
    val consecutiveErrors: Int = 0
) : Serializable {

    /**
     * Whether the ECU is in an error state.
     */
    val isError: Boolean
        get() = communicationStatus == CommunicationStatus.ERROR ||
                communicationStatus == CommunicationStatus.NO_RESPONSE

    /**
     * Whether communication is healthy.
     */
    val isHealthy: Boolean
        get() = communicationStatus == CommunicationStatus.OK && 
                consecutiveErrors == 0

    /**
     * Whether a diagnostic session is active.
     */
    val hasActiveSession: Boolean
        get() = sessionType != null && sessionType != SessionType.DEFAULT

    /**
     * Creates state indicating successful communication.
     */
    fun withSuccess(responseTime: Long): ECUState = copy(
        communicationStatus = CommunicationStatus.OK,
        lastResponseTime = responseTime,
        consecutiveErrors = 0,
        lastError = null
    )

    /**
     * Creates state indicating failed communication.
     */
    fun withError(error: String): ECUState = copy(
        communicationStatus = CommunicationStatus.ERROR,
        consecutiveErrors = consecutiveErrors + 1,
        lastError = error
    )

    /**
     * Creates state with updated session type.
     */
    fun withSession(session: SessionType?): ECUState = copy(
        sessionType = session
    )

    /**
     * Creates state with updated security level.
     */
    fun withSecurityLevel(level: Int?): ECUState = copy(
        securityLevel = level
    )

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Initial state for newly discovered ECU.
         */
        val INITIAL = ECUState(
            communicationStatus = CommunicationStatus.UNKNOWN
        )

        /**
         * State indicating ECU is online and responding.
         */
        val ONLINE = ECUState(
            communicationStatus = CommunicationStatus.OK
        )

        /**
         * State indicating ECU is not responding.
         */
        val OFFLINE = ECUState(
            communicationStatus = CommunicationStatus.NO_RESPONSE
        )
    }
}

// ==================== Communication Status ====================

/**
 * ECU communication status.
 */
enum class CommunicationStatus(
    val displayName: String,
    val isOnline: Boolean
) {
    /** Communication is working normally */
    OK("Online", true),
    
    /** ECU is not responding */
    NO_RESPONSE("No Response", false),
    
    /** Communication error occurred */
    ERROR("Error", false),
    
    /** Communication timed out */
    TIMEOUT("Timeout", false),
    
    /** ECU is busy */
    BUSY("Busy", true),
    
    /** Status unknown / not yet tested */
    UNKNOWN("Unknown", false);

    /**
     * Whether retry is appropriate for this status.
     */
    val shouldRetry: Boolean
        get() = this in listOf(TIMEOUT, BUSY)
}

// ==================== ECU Information Container ====================

/**
 * Container for ECU identification information read via UDS.
 */
data class ECUIdentification(
    val vin: String? = null,
    val ecuName: String? = null,
    val manufacturer: String? = null,
    val hardwarePartNumber: String? = null,
    val hardwareVersion: String? = null,
    val softwarePartNumber: String? = null,
    val softwareVersion: String? = null,
    val bootSoftwareId: String? = null,
    val applicationSoftwareId: String? = null,
    val serialNumber: String? = null,
    val manufacturingDate: String? = null,
    val programmingDate: String? = null,
    val calibrationIds: List<String> = emptyList(),
    val rawData: Map<Int, ByteArray> = emptyMap()
) : Serializable {

    /**
     * Whether any identification data is present.
     */
    val hasData: Boolean
        get() = listOfNotNull(
            vin, ecuName, manufacturer, 
            hardwarePartNumber, softwarePartNumber,
            serialNumber
        ).isNotEmpty()

    /**
     * Applies identification data to an ECU.
     */
    fun applyTo(ecu: ECU): ECU = ecu.copy(
        vin = vin ?: ecu.vin,
        name = ecuName ?: ecu.name,
        manufacturer = manufacturer ?: ecu.manufacturer,
        hardwarePartNumber = hardwarePartNumber ?: ecu.hardwarePartNumber,
        hardwareVersion = hardwareVersion ?: ecu.hardwareVersion,
        softwarePartNumber = softwarePartNumber ?: ecu.softwarePartNumber,
        softwareVersion = softwareVersion ?: ecu.softwareVersion,
        bootSoftwareId = bootSoftwareId ?: ecu.bootSoftwareId,
        applicationSoftwareId = applicationSoftwareId ?: ecu.applicationSoftwareId,
        serialNumber = serialNumber ?: ecu.serialNumber,
        manufacturingDate = manufacturingDate ?: ecu.manufacturingDate,
        programmingDate = programmingDate ?: ecu.programmingDate,
        calibrationIds = calibrationIds.ifEmpty { ecu.calibrationIds }
    )

    companion object {
        private const val serialVersionUID = 1L
    }
}

// ==================== Extension Functions ====================

/**
 * Checks if a list of ECUs contains a specific type.
 */
fun List<ECU>.containsType(type: ECUType): Boolean = 
    any { it.type == type }

/**
 * Finds an ECU by type.
 */
fun List<ECU>.findByType(type: ECUType): ECU? = 
    find { it.type == type }

/**
 * Finds an ECU by address.
 */
fun List<ECU>.findByAddress(address: Int): ECU? = 
    find { it.address == address }

/**
 * Gets all reachable ECUs.
 */
fun List<ECU>.reachable(): List<ECU> = 
    filter { it.isReachable }

/**
 * Gets all ECUs with DTCs.
 */
fun List<ECU>.withDTCs(): List<ECU> = 
    filter { it.hasDTCs }

/**
 * Gets total DTC count across all ECUs.
 */
fun List<ECU>.totalDTCCount(): Int = 
    sumOf { it.state.dtcCount }

/**
 * Gets all powertrain ECUs.
 */
fun List<ECU>.powertrain(): List<ECU> = 
    filter { it.isPowertrainECU }

/**
 * Gets all safety-related ECUs.
 */
fun List<ECU>.safety(): List<ECU> = 
    filter { it.isSafetyECU }
