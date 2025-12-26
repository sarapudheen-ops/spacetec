package com.spacetec.obd.obd

/**
 * DTC (Diagnostic Trouble Code) decoder and database.
 */
object DtcDecoder {

    data class DtcInfo(
        val code: String,
        val system: DtcSystem,
        val description: String,
        val severity: DtcSeverity,
        val possibleCauses: List<String> = emptyList()
    )

    enum class DtcSystem(val prefix: Char, val description: String) {
        POWERTRAIN('P', "Powertrain"),
        CHASSIS('C', "Chassis"),
        BODY('B', "Body"),
        NETWORK('U', "Network")
    }

    enum class DtcSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

    enum class DtcStatus { STORED, PENDING, PERMANENT, CLEARED }

    // Common DTC definitions (subset - full database would have 50k+)
    private val DTC_DATABASE = mapOf(
        // Fuel and Air Metering
        "P0100" to DtcInfo("P0100", DtcSystem.POWERTRAIN, "Mass Air Flow Circuit Malfunction", DtcSeverity.MEDIUM, listOf("Faulty MAF sensor", "Wiring issues", "Air leak")),
        "P0101" to DtcInfo("P0101", DtcSystem.POWERTRAIN, "Mass Air Flow Circuit Range/Performance", DtcSeverity.MEDIUM, listOf("Dirty MAF sensor", "Air filter clogged", "Vacuum leak")),
        "P0102" to DtcInfo("P0102", DtcSystem.POWERTRAIN, "Mass Air Flow Circuit Low Input", DtcSeverity.MEDIUM, listOf("Faulty MAF sensor", "Open circuit", "Low voltage")),
        "P0103" to DtcInfo("P0103", DtcSystem.POWERTRAIN, "Mass Air Flow Circuit High Input", DtcSeverity.MEDIUM, listOf("Faulty MAF sensor", "Short circuit", "High voltage")),
        "P0110" to DtcInfo("P0110", DtcSystem.POWERTRAIN, "Intake Air Temperature Circuit Malfunction", DtcSeverity.LOW, listOf("Faulty IAT sensor", "Wiring issues")),
        "P0115" to DtcInfo("P0115", DtcSystem.POWERTRAIN, "Engine Coolant Temperature Circuit Malfunction", DtcSeverity.MEDIUM, listOf("Faulty ECT sensor", "Wiring issues")),
        "P0120" to DtcInfo("P0120", DtcSystem.POWERTRAIN, "Throttle Position Sensor Circuit Malfunction", DtcSeverity.HIGH, listOf("Faulty TPS", "Wiring issues", "ECM fault")),
        "P0130" to DtcInfo("P0130", DtcSystem.POWERTRAIN, "O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)", DtcSeverity.MEDIUM, listOf("Faulty O2 sensor", "Wiring issues", "Exhaust leak")),
        "P0131" to DtcInfo("P0131", DtcSystem.POWERTRAIN, "O2 Sensor Circuit Low Voltage (Bank 1 Sensor 1)", DtcSeverity.MEDIUM, listOf("Lean condition", "Faulty O2 sensor", "Vacuum leak")),
        "P0132" to DtcInfo("P0132", DtcSystem.POWERTRAIN, "O2 Sensor Circuit High Voltage (Bank 1 Sensor 1)", DtcSeverity.MEDIUM, listOf("Rich condition", "Faulty O2 sensor", "Fuel pressure high")),
        "P0133" to DtcInfo("P0133", DtcSystem.POWERTRAIN, "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)", DtcSeverity.MEDIUM, listOf("Aging O2 sensor", "Exhaust leak", "Fuel contamination")),
        "P0171" to DtcInfo("P0171", DtcSystem.POWERTRAIN, "System Too Lean (Bank 1)", DtcSeverity.MEDIUM, listOf("Vacuum leak", "Faulty MAF", "Low fuel pressure", "Injector issue")),
        "P0172" to DtcInfo("P0172", DtcSystem.POWERTRAIN, "System Too Rich (Bank 1)", DtcSeverity.MEDIUM, listOf("Faulty O2 sensor", "High fuel pressure", "Leaking injector")),
        "P0174" to DtcInfo("P0174", DtcSystem.POWERTRAIN, "System Too Lean (Bank 2)", DtcSeverity.MEDIUM, listOf("Vacuum leak", "Faulty MAF", "Low fuel pressure")),
        "P0175" to DtcInfo("P0175", DtcSystem.POWERTRAIN, "System Too Rich (Bank 2)", DtcSeverity.MEDIUM, listOf("Faulty O2 sensor", "High fuel pressure", "Leaking injector")),
        
        // Ignition System
        "P0300" to DtcInfo("P0300", DtcSystem.POWERTRAIN, "Random/Multiple Cylinder Misfire Detected", DtcSeverity.HIGH, listOf("Spark plugs", "Ignition coils", "Fuel injectors", "Vacuum leak")),
        "P0301" to DtcInfo("P0301", DtcSystem.POWERTRAIN, "Cylinder 1 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #1", "Ignition coil #1", "Injector #1")),
        "P0302" to DtcInfo("P0302", DtcSystem.POWERTRAIN, "Cylinder 2 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #2", "Ignition coil #2", "Injector #2")),
        "P0303" to DtcInfo("P0303", DtcSystem.POWERTRAIN, "Cylinder 3 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #3", "Ignition coil #3", "Injector #3")),
        "P0304" to DtcInfo("P0304", DtcSystem.POWERTRAIN, "Cylinder 4 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #4", "Ignition coil #4", "Injector #4")),
        "P0305" to DtcInfo("P0305", DtcSystem.POWERTRAIN, "Cylinder 5 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #5", "Ignition coil #5", "Injector #5")),
        "P0306" to DtcInfo("P0306", DtcSystem.POWERTRAIN, "Cylinder 6 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #6", "Ignition coil #6", "Injector #6")),
        "P0307" to DtcInfo("P0307", DtcSystem.POWERTRAIN, "Cylinder 7 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #7", "Ignition coil #7", "Injector #7")),
        "P0308" to DtcInfo("P0308", DtcSystem.POWERTRAIN, "Cylinder 8 Misfire Detected", DtcSeverity.HIGH, listOf("Spark plug #8", "Ignition coil #8", "Injector #8")),
        "P0325" to DtcInfo("P0325", DtcSystem.POWERTRAIN, "Knock Sensor 1 Circuit Malfunction", DtcSeverity.MEDIUM, listOf("Faulty knock sensor", "Wiring issues", "Engine knock")),
        "P0335" to DtcInfo("P0335", DtcSystem.POWERTRAIN, "Crankshaft Position Sensor Circuit Malfunction", DtcSeverity.CRITICAL, listOf("Faulty CKP sensor", "Wiring issues", "Timing belt/chain")),
        "P0340" to DtcInfo("P0340", DtcSystem.POWERTRAIN, "Camshaft Position Sensor Circuit Malfunction", DtcSeverity.HIGH, listOf("Faulty CMP sensor", "Wiring issues", "Timing issue")),
        
        // Emission Controls
        "P0400" to DtcInfo("P0400", DtcSystem.POWERTRAIN, "Exhaust Gas Recirculation Flow Malfunction", DtcSeverity.MEDIUM, listOf("Clogged EGR valve", "Carbon buildup", "Vacuum leak")),
        "P0401" to DtcInfo("P0401", DtcSystem.POWERTRAIN, "Exhaust Gas Recirculation Flow Insufficient", DtcSeverity.MEDIUM, listOf("Clogged EGR passages", "Faulty EGR valve", "Vacuum issue")),
        "P0420" to DtcInfo("P0420", DtcSystem.POWERTRAIN, "Catalyst System Efficiency Below Threshold (Bank 1)", DtcSeverity.MEDIUM, listOf("Failing catalytic converter", "O2 sensor issue", "Exhaust leak")),
        "P0430" to DtcInfo("P0430", DtcSystem.POWERTRAIN, "Catalyst System Efficiency Below Threshold (Bank 2)", DtcSeverity.MEDIUM, listOf("Failing catalytic converter", "O2 sensor issue", "Exhaust leak")),
        "P0440" to DtcInfo("P0440", DtcSystem.POWERTRAIN, "Evaporative Emission Control System Malfunction", DtcSeverity.LOW, listOf("Loose gas cap", "EVAP leak", "Purge valve")),
        "P0442" to DtcInfo("P0442", DtcSystem.POWERTRAIN, "Evaporative Emission Control System Leak Detected (Small)", DtcSeverity.LOW, listOf("Loose gas cap", "Small EVAP leak", "Hose crack")),
        "P0446" to DtcInfo("P0446", DtcSystem.POWERTRAIN, "Evaporative Emission Control System Vent Control Circuit", DtcSeverity.LOW, listOf("Faulty vent valve", "Wiring issues", "Blocked vent")),
        "P0455" to DtcInfo("P0455", DtcSystem.POWERTRAIN, "Evaporative Emission Control System Leak Detected (Large)", DtcSeverity.LOW, listOf("Missing gas cap", "Large EVAP leak", "Disconnected hose")),
        
        // Vehicle Speed and Idle
        "P0500" to DtcInfo("P0500", DtcSystem.POWERTRAIN, "Vehicle Speed Sensor Malfunction", DtcSeverity.MEDIUM, listOf("Faulty VSS", "Wiring issues", "Speedometer issue")),
        "P0505" to DtcInfo("P0505", DtcSystem.POWERTRAIN, "Idle Control System Malfunction", DtcSeverity.MEDIUM, listOf("Faulty IAC valve", "Vacuum leak", "Throttle body dirty")),
        "P0506" to DtcInfo("P0506", DtcSystem.POWERTRAIN, "Idle Control System RPM Lower Than Expected", DtcSeverity.LOW, listOf("Vacuum leak", "Dirty throttle body", "IAC issue")),
        "P0507" to DtcInfo("P0507", DtcSystem.POWERTRAIN, "Idle Control System RPM Higher Than Expected", DtcSeverity.LOW, listOf("Vacuum leak", "IAC issue", "Air leak")),
        
        // Transmission
        "P0700" to DtcInfo("P0700", DtcSystem.POWERTRAIN, "Transmission Control System Malfunction", DtcSeverity.HIGH, listOf("TCM fault", "Wiring issues", "Transmission issue")),
        "P0715" to DtcInfo("P0715", DtcSystem.POWERTRAIN, "Input/Turbine Speed Sensor Circuit Malfunction", DtcSeverity.HIGH, listOf("Faulty speed sensor", "Wiring issues", "Transmission issue")),
        "P0720" to DtcInfo("P0720", DtcSystem.POWERTRAIN, "Output Speed Sensor Circuit Malfunction", DtcSeverity.HIGH, listOf("Faulty speed sensor", "Wiring issues", "Transmission issue")),
        "P0730" to DtcInfo("P0730", DtcSystem.POWERTRAIN, "Incorrect Gear Ratio", DtcSeverity.HIGH, listOf("Low transmission fluid", "Worn clutches", "Solenoid issue")),
        "P0741" to DtcInfo("P0741", DtcSystem.POWERTRAIN, "Torque Converter Clutch Circuit Performance", DtcSeverity.MEDIUM, listOf("Faulty TCC solenoid", "Wiring issues", "Valve body")),
        
        // VW/Audi Specific
        "P0016" to DtcInfo("P0016", DtcSystem.POWERTRAIN, "Crankshaft/Camshaft Position Correlation Bank 1 Sensor A", DtcSeverity.HIGH, listOf("Timing chain stretched", "VVT solenoid", "Sensor issue")),
        "P0017" to DtcInfo("P0017", DtcSystem.POWERTRAIN, "Crankshaft/Camshaft Position Correlation Bank 1 Sensor B", DtcSeverity.HIGH, listOf("Timing chain stretched", "VVT solenoid", "Sensor issue")),
        "P2015" to DtcInfo("P2015", DtcSystem.POWERTRAIN, "Intake Manifold Runner Position Sensor/Switch Circuit Bank 1", DtcSeverity.MEDIUM, listOf("Intake manifold flap motor", "Wiring", "Carbon buildup")),
        "P2187" to DtcInfo("P2187", DtcSystem.POWERTRAIN, "System Too Lean at Idle Bank 1", DtcSeverity.MEDIUM, listOf("Vacuum leak", "PCV valve", "Intake gasket")),
        "P2188" to DtcInfo("P2188", DtcSystem.POWERTRAIN, "System Too Rich at Idle Bank 1", DtcSeverity.MEDIUM, listOf("Leaking injector", "Fuel pressure regulator", "EVAP purge")),
        "P2279" to DtcInfo("P2279", DtcSystem.POWERTRAIN, "Intake Air System Leak", DtcSeverity.MEDIUM, listOf("Intake hose crack", "Throttle body gasket", "PCV system")),
        
        // Network/Communication
        "U0100" to DtcInfo("U0100", DtcSystem.NETWORK, "Lost Communication With ECM/PCM", DtcSeverity.CRITICAL, listOf("ECM fault", "CAN bus issue", "Wiring")),
        "U0101" to DtcInfo("U0101", DtcSystem.NETWORK, "Lost Communication With TCM", DtcSeverity.HIGH, listOf("TCM fault", "CAN bus issue", "Wiring")),
        "U0121" to DtcInfo("U0121", DtcSystem.NETWORK, "Lost Communication With ABS", DtcSeverity.HIGH, listOf("ABS module fault", "CAN bus issue", "Wiring")),
        "U0140" to DtcInfo("U0140", DtcSystem.NETWORK, "Lost Communication With BCM", DtcSeverity.MEDIUM, listOf("BCM fault", "CAN bus issue", "Wiring")),
        
        // Body
        "B0001" to DtcInfo("B0001", DtcSystem.BODY, "Driver Frontal Stage 1 Deployment Control", DtcSeverity.CRITICAL, listOf("Airbag system fault", "Wiring", "Airbag module")),
        "B1000" to DtcInfo("B1000", DtcSystem.BODY, "ECU Malfunction", DtcSeverity.HIGH, listOf("Module fault", "Software issue", "Power supply")),
        
        // Chassis
        "C0035" to DtcInfo("C0035", DtcSystem.CHASSIS, "Left Front Wheel Speed Sensor Circuit", DtcSeverity.MEDIUM, listOf("Faulty wheel speed sensor", "Wiring", "Tone ring")),
        "C0040" to DtcInfo("C0040", DtcSystem.CHASSIS, "Right Front Wheel Speed Sensor Circuit", DtcSeverity.MEDIUM, listOf("Faulty wheel speed sensor", "Wiring", "Tone ring")),
        "C0045" to DtcInfo("C0045", DtcSystem.CHASSIS, "Left Rear Wheel Speed Sensor Circuit", DtcSeverity.MEDIUM, listOf("Faulty wheel speed sensor", "Wiring", "Tone ring")),
        "C0050" to DtcInfo("C0050", DtcSystem.CHASSIS, "Right Rear Wheel Speed Sensor Circuit", DtcSeverity.MEDIUM, listOf("Faulty wheel speed sensor", "Wiring", "Tone ring"))
    )

    /**
     * Decode DTC bytes to code string.
     * First byte: bits 7-6 = system, bits 5-4 = first digit, bits 3-0 = second digit
     * Second byte: third and fourth digits
     */
    fun decodeDtcBytes(byte1: Int, byte2: Int): String {
        val system = when ((byte1 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> 'P'
        }
        val digit1 = (byte1 shr 4) and 0x03
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        
        return "$system$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }

    fun getDtcInfo(code: String): DtcInfo {
        return DTC_DATABASE[code.uppercase()] ?: DtcInfo(
            code = code.uppercase(),
            system = when (code.firstOrNull()?.uppercaseChar()) {
                'P' -> DtcSystem.POWERTRAIN
                'C' -> DtcSystem.CHASSIS
                'B' -> DtcSystem.BODY
                'U' -> DtcSystem.NETWORK
                else -> DtcSystem.POWERTRAIN
            },
            description = "Unknown DTC",
            severity = DtcSeverity.INFO
        )
    }

    fun parseDtcResponse(response: String): List<String> {
        val dtcs = mutableListOf<String>()
        val hex = response.replace(Regex("[^0-9A-Fa-f]"), "")
        
        // Skip service response byte (43 for stored, 47 for pending, 4A for permanent)
        var offset = if (hex.length >= 2 && hex.substring(0, 2).uppercase() in listOf("43", "47", "4A")) 2 else 0
        
        // Some responses include DTC count byte
        if (hex.length > offset + 2) {
            val possibleCount = hex.substring(offset, offset + 2).toIntOrNull(16) ?: 0
            if (possibleCount in 0..127 && hex.length >= offset + 2 + possibleCount * 4) {
                offset += 2
            }
        }
        
        while (offset + 4 <= hex.length) {
            val byte1 = hex.substring(offset, offset + 2).toIntOrNull(16) ?: 0
            val byte2 = hex.substring(offset + 2, offset + 4).toIntOrNull(16) ?: 0
            
            if (byte1 != 0 || byte2 != 0) {
                dtcs.add(decodeDtcBytes(byte1, byte2))
            }
            offset += 4
        }
        
        return dtcs
    }
}
