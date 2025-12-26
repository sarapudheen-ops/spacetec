package com.spacetec.obd.core.domain.model.vehicle

/**
 * Utility for decoding Vehicle Identification Numbers (VIN).
 * 
 * VIN structure (17 characters):
 * - Position 1-3: World Manufacturer Identifier (WMI)
 * - Position 4-8: Vehicle Descriptor Section (VDS)
 * - Position 9: Check digit
 * - Position 10: Model year
 * - Position 11: Plant code
 * - Position 12-17: Sequential number
 */
object VinDecoder {
    
    /**
     * Result of VIN decoding.
     */
    data class VinDecodingResult(
        val vin: String,
        val isValid: Boolean,
        val country: String,
        val make: String,
        val year: Int?,
        val model: String?,
        val plant: String?,
        val serialNumber: String?,
        val checkDigitValid: Boolean
    )
    
    /**
     * Characters not allowed in VIN (I, O, Q can be confused with 1, 0).
     */
    private val INVALID_CHARACTERS = setOf('I', 'O', 'Q', 'i', 'o', 'q')
    
    /**
     * Valid VIN characters.
     */
    private val VALID_CHARACTERS = ('A'..'Z').filterNot { it in INVALID_CHARACTERS } + ('0'..'9')
    
    /**
     * Year codes for VIN position 10.
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
     * World Manufacturer Identifier codes (position 1-3).
     */
    private val WMI_CODES = mapOf(
        // USA
        "1G1" to "Chevrolet USA", "1G2" to "Pontiac", "1GC" to "Chevrolet Truck",
        "1GM" to "Pontiac", "1GT" to "GMC Truck", "1G6" to "Cadillac",
        "1FA" to "Ford USA", "1FB" to "Ford Bus", "1FC" to "Ford RV",
        "1FD" to "Ford Truck", "1FM" to "Ford Truck", "1FT" to "Ford Truck",
        "1FU" to "Freightliner", "1FV" to "Freightliner",
        "1C3" to "Chrysler", "1C4" to "Chrysler", "1C6" to "Chrysler",
        "1D3" to "Dodge", "1D4" to "Dodge", "1D7" to "Dodge Truck",
        "1J4" to "Jeep", "1J8" to "Jeep",
        "1L" to "Lincoln", "1LN" to "Lincoln",
        "1ME" to "Mercury", "1MH" to "Mercury",
        "1N4" to "Nissan USA", "1NX" to "NUMMI",
        "1HG" to "Honda USA", "1HD" to "Harley-Davidson",
        
        // Japan
        "JA3" to "Mitsubishi", "JA4" to "Mitsubishi",
        "JF1" to "Subaru", "JF2" to "Subaru",
        "JH4" to "Acura", "JHM" to "Honda", "JHL" to "Honda",
        "JM1" to "Mazda", "JM3" to "Mazda",
        "JN1" to "Nissan", "JN3" to "Nissan", "JN8" to "Nissan",
        "JS1" to "Suzuki", "JS2" to "Suzuki", "JS3" to "Suzuki",
        "JT2" to "Toyota", "JT3" to "Toyota", "JTD" to "Toyota",
        "JTE" to "Toyota", "JTH" to "Toyota", "JTK" to "Toyota",
        "JTL" to "Toyota", "JTM" to "Toyota", "JTN" to "Toyota",
        
        // Germany
        "WA1" to "Audi", "WAU" to "Audi", "WUA" to "Audi",
        "WBA" to "BMW", "WBS" to "BMW M", "WBY" to "BMW i",
        "WDB" to "Mercedes-Benz", "WDC" to "DaimlerChrysler", "WDD" to "Mercedes-Benz",
        "WF0" to "Ford Germany", "WF1" to "Ford Germany",
        "WP0" to "Porsche", "WP1" to "Porsche",
        "WVG" to "Volkswagen", "WVW" to "Volkswagen", "WV1" to "Volkswagen Commercial",
        "WV2" to "Volkswagen Bus",
        
        // UK
        "SAJ" to "Jaguar", "SAL" to "Land Rover", "SCC" to "Lotus",
        "SCF" to "Aston Martin", "SDB" to "Peugeot UK",
        
        // France
        "VF1" to "Renault", "VF3" to "Peugeot", "VF7" to "Citroën",
        
        // Italy
        "ZAM" to "Maserati", "ZAP" to "Piaggio", "ZAR" to "Alfa Romeo",
        "ZCG" to "Cagiva/MV Agusta", "ZDM" to "Ducati", "ZFA" to "Fiat",
        "ZFF" to "Ferrari", "ZHW" to "Lamborghini", "ZLA" to "Lancia",
        
        // Korea
        "KM8" to "Hyundai", "KMH" to "Hyundai",
        "KNA" to "Kia", "KNB" to "Kia", "KNC" to "Kia", "KND" to "Kia",
        
        // Sweden
        "YV1" to "Volvo", "YV2" to "Volvo Bus", "YV3" to "Volvo Truck",
        "YS2" to "Scania", "YS3" to "Saab",
        
        // Czech Republic
        "TMB" to "Škoda",
        
        // Spain
        "VSS" to "SEAT",
        
        // China
        "LFV" to "FAW-VW", "LSG" to "Shanghai GM", "LVS" to "Ford China",
        
        // Mexico
        "3FA" to "Ford Mexico", "3FE" to "Ford Mexico", "3G" to "GM Mexico",
        "3N" to "Nissan Mexico", "3VW" to "VW Mexico",
        
        // Canada
        "2FA" to "Ford Canada", "2G" to "GM Canada", "2HG" to "Honda Canada",
        "2HK" to "Honda Canada", "2HM" to "Hyundai Canada", "2T" to "Toyota Canada"
    )
    
    /**
     * Country codes from first character.
     */
    private val COUNTRY_CODES = mapOf(
        '1' to "United States", '2' to "Canada", '3' to "Mexico",
        '4' to "United States", '5' to "United States",
        '6' to "Australia", '7' to "New Zealand",
        '8' to "South America", '9' to "Brazil",
        'J' to "Japan", 'K' to "Korea",
        'L' to "China", 'M' to "India",
        'S' to "United Kingdom", 'T' to "Switzerland",
        'V' to "France/Spain", 'W' to "Germany",
        'X' to "Russia", 'Y' to "Sweden/Finland",
        'Z' to "Italy"
    )
    
    /**
     * Validates a VIN string.
     */
    fun isValidVin(vin: String): Boolean {
        if (vin.length != 17) return false
        if (vin.any { it.uppercaseChar() in INVALID_CHARACTERS }) return false
        if (!vin.all { it.uppercaseChar() in VALID_CHARACTERS.map { c -> c.uppercaseChar() } }) return false
        return validateCheckDigit(vin)
    }
    
    /**
     * Validates the VIN check digit (position 9).
     */
    fun validateCheckDigit(vin: String): Boolean {
        if (vin.length != 17) return false
        
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        val transliterations = mapOf(
            'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
            'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
            'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9
        )
        
        var sum = 0
        for (i in vin.indices) {
            val char = vin[i].uppercaseChar()
            val value = if (char.isDigit()) char.digitToInt() else transliterations[char] ?: 0
            sum += value * weights[i]
        }
        
        val remainder = sum % 11
        val expectedCheckDigit = if (remainder == 10) 'X' else ('0' + remainder)
        
        return vin[8].uppercaseChar() == expectedCheckDigit
    }
    
    /**
     * Decodes a VIN and returns detailed information.
     */
    fun decode(vin: String): VinDecodingResult {
        val normalizedVin = vin.uppercase().trim()
        val isValid = isValidVin(normalizedVin)
        
        if (normalizedVin.length != 17) {
            return VinDecodingResult(
                vin = normalizedVin,
                isValid = false,
                country = "Unknown",
                make = "Unknown",
                year = null,
                model = null,
                plant = null,
                serialNumber = null,
                checkDigitValid = false
            )
        }
        
        val wmi = normalizedVin.take(3)
        val country = COUNTRY_CODES[normalizedVin[0]] ?: "Unknown"
        val make = findMake(wmi)
        val year = YEAR_CODES[normalizedVin[9]]
        val plant = normalizedVin.getOrNull(10)?.toString()
        val serialNumber = normalizedVin.takeLast(6)
        
        return VinDecodingResult(
            vin = normalizedVin,
            isValid = isValid,
            country = country,
            make = make,
            year = year,
            model = null, // Would need additional database lookup
            plant = plant,
            serialNumber = serialNumber,
            checkDigitValid = validateCheckDigit(normalizedVin)
        )
    }
    
    /**
     * Finds the manufacturer from WMI code.
     */
    private fun findMake(wmi: String): String {
        // Try exact match first
        WMI_CODES[wmi]?.let { return it }
        
        // Try 2-character match
        WMI_CODES.entries.find { it.key.take(2) == wmi.take(2) }?.let { return it.value }
        
        // Try first character match
        WMI_CODES.entries.find { it.key.first() == wmi.first() }?.let { return it.value }
        
        return "Unknown"
    }
    
    /**
     * Extracts the model year from VIN.
     */
    fun getModelYear(vin: String): Int? {
        if (vin.length < 10) return null
        return YEAR_CODES[vin[9].uppercaseChar()]
    }
    
    /**
     * Extracts the manufacturer from VIN.
     */
    fun getManufacturer(vin: String): String {
        if (vin.length < 3) return "Unknown"
        return findMake(vin.take(3).uppercase())
    }
    
    /**
     * Extracts the country of origin from VIN.
     */
    fun getCountryOfOrigin(vin: String): String {
        if (vin.isEmpty()) return "Unknown"
        return COUNTRY_CODES[vin[0].uppercaseChar()] ?: "Unknown"
    }
}