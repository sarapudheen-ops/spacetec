package com.spacetec.obd.core.domain.model.vehicle

import kotlinx.serialization.Serializable

/**
 * Represents a vehicle in the SpaceTec system.
 * 
 * Contains all identifying information and configuration
 * for a specific vehicle, including VIN, make, model,
 * supported protocols, and ECU configuration.
 * 
 * @property id Unique identifier in the local database
 * @property vin Vehicle Identification Number (17 characters)
 * @property make Vehicle manufacturer (e.g., "Toyota")
 * @property model Vehicle model (e.g., "Camry")
 * @property year Model year
 * @property nickname User-assigned nickname
 * @property licensePlate License plate number
 * @property fuelType Type of fuel used
 * @property engineType Engine configuration
 * @property transmissionType Transmission type
 * @property driveType Drive configuration
 * @property mileage Current mileage/odometer reading
 * @property mileageUnit Unit for mileage (km or miles)
 * @property supportedProtocols List of supported diagnostic protocols
 * @property ecus List of detected ECUs
 * @property createdAt Timestamp when vehicle was added
 * @property lastConnected Timestamp of last diagnostic session
 */
@Serializable
data class Vehicle(
    val id: Long = 0,
    val vin: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val nickname: String = "",
    val licensePlate: String = "",
    val fuelType: FuelType = FuelType.UNKNOWN,
    val engineType: String = "",
    val engineDisplacement: Float = 0f,
    val transmissionType: TransmissionType = TransmissionType.UNKNOWN,
    val driveType: DriveType = DriveType.UNKNOWN,
    val mileage: Long = 0,
    val mileageUnit: MileageUnit = MileageUnit.KILOMETERS,
    val supportedProtocols: List<String> = emptyList(),
    val ecus: List<Ecu> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnected: Long = 0,
    val color: String = "",
    val trimLevel: String = "",
    val bodyStyle: String = "",
    val countryOfOrigin: String = "",
    val manufacturerPlant: String = "",
    val productionSequence: String = ""
) {
    /**
     * Display name for the vehicle (nickname or make/model/year).
     */
    val displayName: String
        get() = nickname.ifEmpty { "$year $make $model".trim() }
    
    /**
     * Short display name.
     */
    val shortDisplayName: String
        get() = nickname.ifEmpty { "$make $model".trim() }
    
    /**
     * Whether VIN is valid (17 characters, proper format).
     */
    val hasValidVin: Boolean
        get() = VinDecoder.isValidVin(vin)
    
    /**
     * Formatted mileage string with unit.
     */
    val mileageDisplay: String
        get() = "%,d %s".format(mileage, mileageUnit.abbreviation)
    
    /**
     * Gets ECU by name.
     */
    fun getEcu(name: String): Ecu? =
        ecus.find { it.name.equals(name, ignoreCase = true) }
    
    /**
     * Gets ECU by address.
     */
    fun getEcuByAddress(address: String): Ecu? =
        ecus.find { it.address.equals(address, ignoreCase = true) }
    
    /**
     * Gets all ECUs that reported DTCs in the last session.
     */
    fun getEcusWithDtcs(): List<Ecu> =
        ecus.filter { it.lastDtcCount > 0 }
    
    /**
     * Checks if vehicle supports a specific protocol.
     */
    fun supportsProtocol(protocol: String): Boolean =
        supportedProtocols.any { it.equals(protocol, ignoreCase = true) }
    
    companion object {
        /**
         * Creates a vehicle from VIN decoding.
         */
        fun fromVin(vin: String): Vehicle {
            val decoded = VinDecoder.decode(vin)
            return Vehicle(
                vin = vin.uppercase(),
                make = decoded.make,
                model = decoded.model ?: "",
                year = decoded.year ?: 0,
                countryOfOrigin = decoded.country,
                manufacturerPlant = decoded.plant ?: ""
            )
        }
        
        /**
         * Creates an empty/unknown vehicle.
         */
        fun unknown(): Vehicle = Vehicle(
            make = "Unknown",
            model = "Vehicle"
        )
    }
}

/**
 * Represents an Electronic Control Unit (ECU) in a vehicle.
 */
@Serializable
data class Ecu(
    val address: String,
    val name: String,
    val description: String = "",
    val manufacturer: String = "",
    val hardwareVersion: String = "",
    val softwareVersion: String = "",
    val calibrationId: String = "",
    val canId: Int = 0,
    val protocol: String = "",
    val isSupported: Boolean = true,
    val lastScanned: Long = 0,
    val lastDtcCount: Int = 0,
    val supportedServices: List<Int> = emptyList(),
    val supportedPids: List<Int> = emptyList()
) {
    /**
     * Functional group of the ECU.
     */
    val functionalGroup: EcuFunctionalGroup
        get() = EcuFunctionalGroup.fromName(name)
    
    /**
     * Checks if ECU supports a specific OBD-II service.
     */
    fun supportsService(serviceId: Int): Boolean =
        supportedServices.contains(serviceId)
    
    /**
     * Checks if ECU supports a specific PID.
     */
    fun supportsPid(pid: Int): Boolean =
        supportedPids.contains(pid)
    
    companion object {
        // Common ECU addresses
        const val ADDRESS_ENGINE = "7E0"
        const val ADDRESS_TRANSMISSION = "7E1"
        const val ADDRESS_ABS = "7B0"
        const val ADDRESS_AIRBAG = "720"
        const val ADDRESS_BODY = "760"
        const val ADDRESS_CLIMATE = "7C4"
        const val ADDRESS_INSTRUMENT = "720"
        
        // Common ECU names
        const val NAME_ENGINE = "Engine"
        const val NAME_TRANSMISSION = "Transmission"
        const val NAME_ABS = "ABS"
        const val NAME_AIRBAG = "Airbag"
        const val NAME_BODY = "Body Control"
        const val NAME_CLIMATE = "Climate Control"
        const val NAME_INSTRUMENT = "Instrument Cluster"
        const val NAME_STEERING = "Power Steering"
        const val NAME_SUSPENSION = "Suspension"
    }
}

/**
 * Functional groups for ECUs.
 */
@Serializable
enum class EcuFunctionalGroup(val displayName: String) {
    POWERTRAIN("Powertrain"),
    CHASSIS("Chassis"),
    BODY("Body"),
    NETWORK("Network"),
    SAFETY("Safety"),
    INFOTAINMENT("Infotainment"),
    OTHER("Other");
    
    companion object {
        fun fromName(ecuName: String): EcuFunctionalGroup {
            val name = ecuName.lowercase()
            return when {
                name.contains("engine") || name.contains("transmission") || 
                name.contains("ecm") || name.contains("pcm") || name.contains("tcm") -> POWERTRAIN
                
                name.contains("abs") || name.contains("brake") || 
                name.contains("steering") || name.contains("suspension") -> CHASSIS
                
                name.contains("body") || name.contains("bcm") || 
                name.contains("door") || name.contains("window") -> BODY
                
                name.contains("airbag") || name.contains("srs") || 
                name.contains("restraint") -> SAFETY
                
                name.contains("radio") || name.contains("navigation") || 
                name.contains("infotainment") || name.contains("audio") -> INFOTAINMENT
                
                name.contains("gateway") || name.contains("can") -> NETWORK
                
                else -> OTHER
            }
        }
    }
}

/**
 * Fuel types.
 */
@Serializable
enum class FuelType(val displayName: String) {
    GASOLINE("Gasoline"),
    DIESEL("Diesel"),
    ELECTRIC("Electric"),
    HYBRID("Hybrid"),
    PLUG_IN_HYBRID("Plug-in Hybrid"),
    FLEX_FUEL("Flex Fuel"),
    HYDROGEN("Hydrogen"),
    CNG("CNG"),
    LPG("LPG"),
    UNKNOWN("Unknown")
}

/**
 * Transmission types.
 */
@Serializable
enum class TransmissionType(val displayName: String) {
    MANUAL("Manual"),
    AUTOMATIC("Automatic"),
    CVT("CVT"),
    DCT("Dual-Clutch"),
    AUTOMATED_MANUAL("Automated Manual"),
    ELECTRIC("Electric (Single Speed)"),
    UNKNOWN("Unknown")
}

/**
 * Drive types.
 */
@Serializable
enum class DriveType(val displayName: String) {
    FWD("Front-Wheel Drive"),
    RWD("Rear-Wheel Drive"),
    AWD("All-Wheel Drive"),
    FOUR_WD("4-Wheel Drive"),
    UNKNOWN("Unknown")
}

/**
 * Mileage units.
 */
@Serializable
enum class MileageUnit(val displayName: String, val abbreviation: String) {
    KILOMETERS("Kilometers", "km"),
    MILES("Miles", "mi")
}