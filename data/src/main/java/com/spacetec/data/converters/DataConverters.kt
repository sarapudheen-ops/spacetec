/*
 * Copyright 2024 SpaceTec Automotive Diagnostics
 * Licensed under the Apache License, Version 2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spacetec.core.database.converters

import android.net.Uri
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal
import java.net.InetAddress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.UUID

/**
 * Room Type Converters for the SpaceTec database.
 *
 * This class provides comprehensive type conversion between Kotlin/Java types and
 * SQLite-compatible storage formats. All converters handle null values gracefully
 * and use consistent serialization formats for data integrity.
 *
 * ## Conversion Categories
 *
 * ### Date/Time Converters
 * - [Date] ↔ [Long] (Unix timestamp in milliseconds)
 * - [Instant] ↔ [Long] (Epoch milliseconds)
 * - [LocalDate] ↔ [String] (ISO-8601: yyyy-MM-dd)
 * - [LocalDateTime] ↔ [String] (ISO-8601: yyyy-MM-ddTHH:mm:ss)
 * - [ZonedDateTime] ↔ [String] (ISO-8601 with zone)
 * - [Duration] ↔ [Long] (Milliseconds)
 *
 * ### Collection Converters
 * - [List]<[String]> ↔ [String] (JSON array)
 * - [List]<[Int]> ↔ [String] (JSON array)
 * - [List]<[Long]> ↔ [String] (JSON array)
 * - [Set]<[String]> ↔ [String] (JSON array)
 * - [Map]<[String], [String]> ↔ [String] (JSON object)
 * - [Map]<[String], [Any]?> ↔ [String] (JSON object)
 *
 * ### Binary Data Converters
 * - [ByteArray] ↔ [String] (Hexadecimal encoding)
 *
 * ### Custom Type Converters
 * - [UUID] ↔ [String]
 * - [BigDecimal] ↔ [String]
 * - [Uri] ↔ [String]
 * - [InetAddress] ↔ [String]
 *
 * ### Domain-Specific Converters
 * - [ECUAddress] ↔ [Int]
 * - [DTCCode] ↔ [String]
 * - [VIN] ↔ [String]
 *
 * ## Thread Safety
 * All converters are stateless and thread-safe. The [Json] instance is configured
 * for lenient parsing to handle legacy data gracefully.
 *
 * ## Error Handling
 * Converters return null for invalid input rather than throwing exceptions,
 * ensuring database operations don't crash on malformed data.
 *
 * @see androidx.room.TypeConverters
 */
class DateConverters {

    companion object {
        /**
         * Shared JSON configuration for all serialization operations.
         * Configured with:
         * - Lenient parsing for backward compatibility
         * - Ignore unknown keys for forward compatibility
         * - No pretty printing for storage efficiency
         */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        /**
         * Characters used for hexadecimal encoding.
         */
        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

        /**
         * ISO-8601 date formatter for [LocalDate].
         * Format: yyyy-MM-dd (e.g., "2024-01-15")
         */
        private val LOCAL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * ISO-8601 date-time formatter for [LocalDateTime].
         * Format: yyyy-MM-ddTHH:mm:ss (e.g., "2024-01-15T14:30:00")
         */
        private val LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        /**
         * ISO-8601 zoned date-time formatter for [ZonedDateTime].
         * Format: yyyy-MM-ddTHH:mm:ssXXX[ZoneId] (e.g., "2024-01-15T14:30:00+01:00[Europe/Paris]")
         */
        private val ZONED_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
    }

    // ==================== DATE/TIME CONVERTERS ====================

    /**
     * Converts a [Date] to a [Long] timestamp.
     *
     * @param date The date to convert, may be null
     * @return Unix timestamp in milliseconds, or null if input is null
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    /**
     * Converts a [Long] timestamp to a [Date].
     *
     * @param timestamp Unix timestamp in milliseconds, may be null
     * @return Corresponding [Date] object, or null if input is null
     */
    @TypeConverter
    fun timestampToDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }

    /**
     * Converts an [Instant] to a [Long] epoch milliseconds value.
     *
     * @param instant The instant to convert, may be null
     * @return Epoch milliseconds, or null if input is null
     */
    @TypeConverter
    fun instantToLong(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    /**
     * Converts a [Long] epoch milliseconds value to an [Instant].
     *
     * @param epochMilli Epoch milliseconds, may be null
     * @return Corresponding [Instant], or null if input is null
     */
    @TypeConverter
    fun longToInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }

    /**
     * Converts a [LocalDate] to an ISO-8601 formatted [String].
     *
     * @param localDate The date to convert, may be null
     * @return ISO-8601 date string (yyyy-MM-dd), or null if input is null
     */
    @TypeConverter
    fun localDateToString(localDate: LocalDate?): String? {
        return localDate?.format(LOCAL_DATE_FORMATTER)
    }

    /**
     * Converts an ISO-8601 formatted [String] to a [LocalDate].
     *
     * @param dateString ISO-8601 date string, may be null
     * @return Corresponding [LocalDate], or null if input is null or invalid
     */
    @TypeConverter
    fun stringToLocalDate(dateString: String?): LocalDate? {
        return dateString?.let {
            try {
                LocalDate.parse(it, LOCAL_DATE_FORMATTER)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [LocalDateTime] to an ISO-8601 formatted [String].
     *
     * @param localDateTime The date-time to convert, may be null
     * @return ISO-8601 date-time string, or null if input is null
     */
    @TypeConverter
    fun localDateTimeToString(localDateTime: LocalDateTime?): String? {
        return localDateTime?.format(LOCAL_DATE_TIME_FORMATTER)
    }

    /**
     * Converts an ISO-8601 formatted [String] to a [LocalDateTime].
     *
     * @param dateTimeString ISO-8601 date-time string, may be null
     * @return Corresponding [LocalDateTime], or null if input is null or invalid
     */
    @TypeConverter
    fun stringToLocalDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let {
            try {
                LocalDateTime.parse(it, LOCAL_DATE_TIME_FORMATTER)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [ZonedDateTime] to an ISO-8601 formatted [String] with zone information.
     *
     * @param zonedDateTime The zoned date-time to convert, may be null
     * @return ISO-8601 zoned date-time string, or null if input is null
     */
    @TypeConverter
    fun zonedDateTimeToString(zonedDateTime: ZonedDateTime?): String? {
        return zonedDateTime?.format(ZONED_DATE_TIME_FORMATTER)
    }

    /**
     * Converts an ISO-8601 formatted [String] with zone information to a [ZonedDateTime].
     *
     * @param dateTimeString ISO-8601 zoned date-time string, may be null
     * @return Corresponding [ZonedDateTime], or null if input is null or invalid
     */
    @TypeConverter
    fun stringToZonedDateTime(dateTimeString: String?): ZonedDateTime? {
        return dateTimeString?.let {
            try {
                ZonedDateTime.parse(it, ZONED_DATE_TIME_FORMATTER)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [Duration] to [Long] milliseconds.
     *
     * @param duration The duration to convert, may be null
     * @return Duration in milliseconds, or null if input is null
     */
    @TypeConverter
    fun durationToLong(duration: Duration?): Long? {
        return duration?.toMillis()
    }

    /**
     * Converts [Long] milliseconds to a [Duration].
     *
     * @param millis Duration in milliseconds, may be null
     * @return Corresponding [Duration], or null if input is null
     */
    @TypeConverter
    fun longToDuration(millis: Long?): Duration? {
        return millis?.let { Duration.ofMillis(it) }
    }

    // ==================== COLLECTION CONVERTERS ====================

    /**
     * Converts a [List] of [String] to a JSON array [String].
     *
     * @param list The list to convert, may be null
     * @return JSON array string, or null if input is null
     */
    @TypeConverter
    fun stringListToJson(list: List<String>?): String? {
        return list?.let { json.encodeToString(it) }
    }

    /**
     * Converts a JSON array [String] to a [List] of [String].
     *
     * @param jsonString JSON array string, may be null
     * @return Corresponding list, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToStringList(jsonString: String?): List<String>? {
        return jsonString?.let {
            try {
                json.decodeFromString<List<String>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [List] of [Int] to a JSON array [String].
     *
     * @param list The list to convert, may be null
     * @return JSON array string, or null if input is null
     */
    @TypeConverter
    fun intListToJson(list: List<Int>?): String? {
        return list?.let { json.encodeToString(it) }
    }

    /**
     * Converts a JSON array [String] to a [List] of [Int].
     *
     * @param jsonString JSON array string, may be null
     * @return Corresponding list, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToIntList(jsonString: String?): List<Int>? {
        return jsonString?.let {
            try {
                json.decodeFromString<List<Int>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [List] of [Long] to a JSON array [String].
     *
     * @param list The list to convert, may be null
     * @return JSON array string, or null if input is null
     */
    @TypeConverter
    fun longListToJson(list: List<Long>?): String? {
        return list?.let { json.encodeToString(it) }
    }

    /**
     * Converts a JSON array [String] to a [List] of [Long].
     *
     * @param jsonString JSON array string, may be null
     * @return Corresponding list, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToLongList(jsonString: String?): List<Long>? {
        return jsonString?.let {
            try {
                json.decodeFromString<List<Long>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [Set] of [String] to a JSON array [String].
     *
     * @param set The set to convert, may be null
     * @return JSON array string, or null if input is null
     */
    @TypeConverter
    fun stringSetToJson(set: Set<String>?): String? {
        return set?.let { json.encodeToString(it.toList()) }
    }

    /**
     * Converts a JSON array [String] to a [Set] of [String].
     *
     * @param jsonString JSON array string, may be null
     * @return Corresponding set, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToStringSet(jsonString: String?): Set<String>? {
        return jsonString?.let {
            try {
                json.decodeFromString<List<String>>(it).toSet()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [Map] of [String] to [String] to a JSON object [String].
     *
     * @param map The map to convert, may be null
     * @return JSON object string, or null if input is null
     */
    @TypeConverter
    fun stringMapToJson(map: Map<String, String>?): String? {
        return map?.let { json.encodeToString(it) }
    }

    /**
     * Converts a JSON object [String] to a [Map] of [String] to [String].
     *
     * @param jsonString JSON object string, may be null
     * @return Corresponding map, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToStringMap(jsonString: String?): Map<String, String>? {
        return jsonString?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [Map] of [String] to [Any]? to a JSON object [String].
     *
     * Supports the following value types:
     * - String
     * - Number (Int, Long, Float, Double)
     * - Boolean
     * - null
     * - Nested Map<String, Any?>
     * - List<Any?>
     *
     * @param map The map to convert, may be null
     * @return JSON object string, or null if input is null
     */
    @TypeConverter
    fun anyMapToJson(map: Map<String, Any?>?): String? {
        return map?.let {
            try {
                val jsonObject = mapToJsonElement(it)
                json.encodeToString(JsonElement.serializer(), jsonObject)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a JSON object [String] to a [Map] of [String] to [Any]?.
     *
     * @param jsonString JSON object string, may be null
     * @return Corresponding map with proper types, or null if input is null or invalid
     */
    @TypeConverter
    fun jsonToAnyMap(jsonString: String?): Map<String, Any?>? {
        return jsonString?.let {
            try {
                val jsonElement = json.decodeFromString(JsonElement.serializer(), it)
                jsonElementToMap(jsonElement)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Recursively converts a Map to a JsonElement.
     */
    private fun mapToJsonElement(map: Map<String, Any?>): JsonObject {
        val content = map.mapValues { (_, value) -> anyToJsonElement(value) }
        return JsonObject(content)
    }

    /**
     * Converts any supported value to a JsonElement.
     */
    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonElement(value as Map<String, Any?>)
            }
            is List<*> -> {
                JsonArray(value.map { anyToJsonElement(it) })
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Recursively converts a JsonElement to a Map.
     */
    private fun jsonElementToMap(element: JsonElement): Map<String, Any?>? {
        return when (element) {
            is JsonObject -> {
                element.mapValues { (_, v) -> jsonElementToAny(v) }
            }
            else -> null
        }
    }

    /**
     * Converts a JsonElement to the appropriate Kotlin type.
     */
    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.booleanOrNull
                    element.intOrNull != null -> element.intOrNull
                    element.longOrNull != null -> element.longOrNull
                    element.floatOrNull != null -> element.floatOrNull
                    element.doubleOrNull != null -> element.doubleOrNull
                    else -> element.content
                }
            }
            is JsonArray -> element.map { jsonElementToAny(it) }
            is JsonObject -> element.mapValues { (_, v) -> jsonElementToAny(v) }
        }
    }

    // ==================== BYTE ARRAY CONVERTERS ====================

    /**
     * Converts a [ByteArray] to a hexadecimal [String].
     *
     * Each byte is represented as two uppercase hexadecimal characters.
     * For example: [0x1A, 0x2B, 0x3C] → "1A2B3C"
     *
     * @param bytes The byte array to convert, may be null
     * @return Uppercase hexadecimal string, or null if input is null
     */
    @TypeConverter
    fun byteArrayToHexString(bytes: ByteArray?): String? {
        return bytes?.let { byteArray ->
            buildString(byteArray.size * 2) {
                byteArray.forEach { byte ->
                    val octet = byte.toInt()
                    append(HEX_CHARS[(octet and 0xF0) ushr 4])
                    append(HEX_CHARS[octet and 0x0F])
                }
            }
        }
    }

    /**
     * Converts a hexadecimal [String] to a [ByteArray].
     *
     * The input string should contain pairs of hexadecimal characters.
     * Both uppercase and lowercase characters are supported.
     * For example: "1A2B3C" → [0x1A, 0x2B, 0x3C]
     *
     * @param hexString Hexadecimal string, may be null
     * @return Corresponding byte array, or null if input is null or invalid
     */
    @TypeConverter
    fun hexStringToByteArray(hexString: String?): ByteArray? {
        return hexString?.let { hex ->
            try {
                val cleanHex = hex.replace(" ", "").uppercase()
                require(cleanHex.length % 2 == 0) { "Hex string must have even length" }

                ByteArray(cleanHex.length / 2) { index ->
                    val hexPair = cleanHex.substring(index * 2, index * 2 + 2)
                    hexPair.toInt(16).toByte()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==================== CUSTOM TYPE CONVERTERS ====================

    /**
     * Converts a [UUID] to its [String] representation.
     *
     * @param uuid The UUID to convert, may be null
     * @return UUID string in standard format (8-4-4-4-12), or null if input is null
     */
    @TypeConverter
    fun uuidToString(uuid: UUID?): String? {
        return uuid?.toString()
    }

    /**
     * Converts a [String] to a [UUID].
     *
     * @param uuidString UUID string in standard format, may be null
     * @return Corresponding UUID, or null if input is null or invalid
     */
    @TypeConverter
    fun stringToUuid(uuidString: String?): UUID? {
        return uuidString?.let {
            try {
                UUID.fromString(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [BigDecimal] to its [String] representation.
     *
     * Uses [BigDecimal.toPlainString] to avoid scientific notation.
     *
     * @param bigDecimal The BigDecimal to convert, may be null
     * @return Plain string representation, or null if input is null
     */
    @TypeConverter
    fun bigDecimalToString(bigDecimal: BigDecimal?): String? {
        return bigDecimal?.toPlainString()
    }

    /**
     * Converts a [String] to a [BigDecimal].
     *
     * @param value String representation of a decimal number, may be null
     * @return Corresponding BigDecimal, or null if input is null or invalid
     */
    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? {
        return value?.let {
            try {
                BigDecimal(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts a [Uri] to its [String] representation.
     *
     * @param uri The URI to convert, may be null
     * @return URI string, or null if input is null
     */
    @TypeConverter
    fun uriToString(uri: Uri?): String? {
        return uri?.toString()
    }

    /**
     * Converts a [String] to a [Uri].
     *
     * @param uriString URI string, may be null
     * @return Corresponding Uri, or null if input is null or invalid
     */
    @TypeConverter
    fun stringToUri(uriString: String?): Uri? {
        return uriString?.let {
            try {
                Uri.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts an [InetAddress] to its [String] representation.
     *
     * @param address The InetAddress to convert, may be null
     * @return Host address string, or null if input is null
     */
    @TypeConverter
    fun inetAddressToString(address: InetAddress?): String? {
        return address?.hostAddress
    }

    /**
     * Converts a [String] to an [InetAddress].
     *
     * @param addressString Host address string, may be null
     * @return Corresponding InetAddress, or null if input is null or invalid
     */
    @TypeConverter
    fun stringToInetAddress(addressString: String?): InetAddress? {
        return addressString?.let {
            try {
                InetAddress.getByName(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==================== DOMAIN-SPECIFIC CONVERTERS ====================

    /**
     * Converts an [ECUAddress] value class to its [Int] representation.
     *
     * @param address The ECU address to convert, may be null
     * @return Integer address value, or null if input is null
     */
    @TypeConverter
    fun ecuAddressToInt(address: ECUAddress?): Int? {
        return address?.value
    }

    /**
     * Converts an [Int] to an [ECUAddress] value class.
     *
     * @param value Integer address value, may be null
     * @return Corresponding ECUAddress, or null if input is null
     */
    @TypeConverter
    fun intToEcuAddress(value: Int?): ECUAddress? {
        return value?.let { ECUAddress(it) }
    }

    /**
     * Converts a [DTCCode] value class to its [String] representation.
     *
     * @param code The DTC code to convert, may be null
     * @return DTC code string (e.g., "P0301"), or null if input is null
     */
    @TypeConverter
    fun dtcCodeToString(code: DTCCode?): String? {
        return code?.value
    }

    /**
     * Converts a [String] to a [DTCCode] value class.
     *
     * @param value DTC code string (e.g., "P0301"), may be null
     * @return Corresponding DTCCode, or null if input is null
     */
    @TypeConverter
    fun stringToDtcCode(value: String?): DTCCode? {
        return value?.let { DTCCode(it) }
    }

    /**
     * Converts a [VIN] value class to its [String] representation.
     *
     * @param vin The VIN to convert, may be null
     * @return 17-character VIN string, or null if input is null
     */
    @TypeConverter
    fun vinToString(vin: VIN?): String? {
        return vin?.value
    }

    /**
     * Converts a [String] to a [VIN] value class.
     *
     * @param value 17-character VIN string, may be null
     * @return Corresponding VIN, or null if input is null
     */
    @TypeConverter
    fun stringToVin(value: String?): VIN? {
        return value?.let { VIN(it) }
    }
}

// ==================== DOMAIN VALUE CLASSES ====================

/**
 * Value class representing an ECU address.
 *
 * ECU addresses are typically 11-bit or 29-bit identifiers used in CAN communication.
 * Common examples:
 * - 0x7E0: Engine ECU request
 * - 0x7E8: Engine ECU response
 * - 0x7DF: Functional broadcast address
 *
 * @property value The raw integer address value
 */
@JvmInline
value class ECUAddress(val value: Int) {
    
    /**
     * Returns the hexadecimal representation of the address.
     * 
     * @return Hex string prefixed with "0x" (e.g., "0x7E0")
     */
    fun toHexString(): String = "0x${value.toString(16).uppercase()}"
    
    /**
     * Returns the hexadecimal representation without prefix.
     * 
     * @return Hex string (e.g., "7E0")
     */
    fun toHexStringNoPrefix(): String = value.toString(16).uppercase()
    
    override fun toString(): String = toHexString()
    
    companion object {
        /** Standard OBD-II functional broadcast address */
        val FUNCTIONAL_BROADCAST = ECUAddress(0x7DF)
        
        /** Invalid/unknown address marker */
        val INVALID = ECUAddress(-1)
        
        /**
         * Creates an ECUAddress from a hexadecimal string.
         *
         * @param hex Hexadecimal string, with or without "0x" prefix
         * @return Corresponding ECUAddress
         * @throws NumberFormatException if the string is not valid hex
         */
        fun fromHexString(hex: String): ECUAddress {
            val cleanHex = hex.removePrefix("0x").removePrefix("0X")
            return ECUAddress(cleanHex.toInt(16))
        }
    }
}

/**
 * Value class representing a Diagnostic Trouble Code (DTC).
 *
 * DTCs follow the SAE J2012 format:
 * - First character: System (P=Powertrain, C=Chassis, B=Body, U=Network)
 * - Second character: Type (0=Generic/SAE, 1=Manufacturer)
 * - Remaining characters: Specific fault identifier
 *
 * Examples: P0300, P0171, B1234, U0100
 *
 * @property value The DTC code string
 */
@JvmInline
value class DTCCode(val value: String) {
    
    /**
     * The system category of the DTC.
     */
    val system: DTCSystem
        get() = when (value.firstOrNull()?.uppercaseChar()) {
            'P' -> DTCSystem.POWERTRAIN
            'C' -> DTCSystem.CHASSIS
            'B' -> DTCSystem.BODY
            'U' -> DTCSystem.NETWORK
            else -> DTCSystem.UNKNOWN
        }
    
    /**
     * Whether this is a generic (SAE) or manufacturer-specific code.
     */
    val isGeneric: Boolean
        get() = value.getOrNull(1) == '0'
    
    /**
     * Whether this is a manufacturer-specific code.
     */
    val isManufacturerSpecific: Boolean
        get() = value.getOrNull(1) == '1'
    
    /**
     * The numeric portion of the DTC (last 3-4 digits).
     */
    val numericCode: Int
        get() = value.drop(1).toIntOrNull() ?: 0
    
    /**
     * Validates that the DTC format is correct.
     *
     * @return true if the DTC matches the standard format
     */
    fun isValid(): Boolean {
        if (value.length !in 5..6) return false
        val firstChar = value.firstOrNull()?.uppercaseChar() ?: return false
        if (firstChar !in listOf('P', 'C', 'B', 'U')) return false
        val numericPart = value.drop(1)
        return numericPart.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
    }
    
    override fun toString(): String = value.uppercase()
    
    companion object {
        /** Regex pattern for valid DTC format */
        private val DTC_PATTERN = Regex("^[PCBU][0-3][0-9A-Fa-f]{3}\$")
        
        /**
         * Creates a DTCCode if the input is valid, null otherwise.
         *
         * @param code The DTC code string to parse
         * @return DTCCode if valid, null otherwise
         */
        fun parseOrNull(code: String): DTCCode? {
            val normalized = code.uppercase().trim()
            return if (DTC_PATTERN.matches(normalized)) DTCCode(normalized) else null
        }
        
        /**
         * Converts raw OBD-II bytes to a DTC code string.
         *
         * @param byte1 First DTC byte
         * @param byte2 Second DTC byte
         * @return Formatted DTC code
         */
        fun fromBytes(byte1: Int, byte2: Int): DTCCode {
            val firstChar = when ((byte1 shr 6) and 0x03) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                3 -> 'U'
                else -> 'P'
            }
            val secondChar = ((byte1 shr 4) and 0x03).toString()
            val thirdChar = (byte1 and 0x0F).toString(16).uppercase()
            val fourthFifth = byte2.toString(16).uppercase().padStart(2, '0')
            
            return DTCCode("$firstChar$secondChar$thirdChar$fourthFifth")
        }
    }
}

/**
 * Enumeration of DTC system categories.
 */
enum class DTCSystem {
    /** Powertrain - Engine, Transmission (P codes) */
    POWERTRAIN,
    /** Chassis - ABS, Steering, Suspension (C codes) */
    CHASSIS,
    /** Body - Airbags, AC, Lighting (B codes) */
    BODY,
    /** Network - Communication, CAN bus (U codes) */
    NETWORK,
    /** Unknown or invalid system */
    UNKNOWN
}

/**
 * Value class representing a Vehicle Identification Number (VIN).
 *
 * VINs are 17-character identifiers that encode:
 * - World Manufacturer Identifier (WMI): Characters 1-3
 * - Vehicle Descriptor Section (VDS): Characters 4-9
 * - Vehicle Identifier Section (VIS): Characters 10-17
 *
 * VINs do not contain I, O, or Q to avoid confusion with 1, 0, and 9.
 *
 * @property value The 17-character VIN string
 */
@JvmInline
value class VIN(val value: String) {
    
    /**
     * World Manufacturer Identifier (first 3 characters).
     * Identifies the manufacturer and country of origin.
     */
    val wmi: String
        get() = value.take(3)
    
    /**
     * Vehicle Descriptor Section (characters 4-9).
     * Describes vehicle attributes like model, body type, engine.
     */
    val vds: String
        get() = value.substring(3, 9)
    
    /**
     * Vehicle Identifier Section (characters 10-17).
     * Contains the model year, plant code, and serial number.
     */
    val vis: String
        get() = value.substring(9, 17)
    
    /**
     * Model year character (10th position).
     */
    val modelYearChar: Char
        get() = value.getOrElse(9) { '0' }
    
    /**
     * Decodes the model year from the VIN.
     *
     * @return The model year, or null if the year code is invalid
     */
    val modelYear: Int?
        get() = decodeModelYear(modelYearChar)
    
    /**
     * Plant/Factory code (11th position).
     */
    val plantCode: Char
        get() = value.getOrElse(10) { '0' }
    
    /**
     * Serial number (last 6 characters).
     */
    val serialNumber: String
        get() = value.takeLast(6)
    
    /**
     * Check digit (9th position).
     * Used to validate VIN authenticity.
     */
    val checkDigit: Char
        get() = value.getOrElse(8) { '0' }
    
    /**
     * Validates the VIN format and check digit.
     *
     * @return true if the VIN is valid
     */
    fun isValid(): Boolean {
        if (value.length != 17) return false
        if (value.any { it.uppercaseChar() in listOf('I', 'O', 'Q') }) return false
        return validateCheckDigit()
    }
    
    /**
     * Validates the check digit using the standard VIN algorithm.
     */
    private fun validateCheckDigit(): Boolean {
        val transliterations = mapOf(
            'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
            'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
            'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9
        )
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        
        var sum = 0
        for (i in value.indices) {
            val char = value[i].uppercaseChar()
            val charValue = if (char.isDigit()) char.digitToInt() else transliterations[char] ?: return false
            sum += charValue * weights[i]
        }
        
        val remainder = sum % 11
        val expectedCheckDigit = if (remainder == 10) 'X' else ('0' + remainder)
        
        return checkDigit.uppercaseChar() == expectedCheckDigit
    }
    
    /**
     * Returns a masked version of the VIN for display/logging.
     * Shows first 3 and last 4 characters only.
     */
    fun masked(): String = "${value.take(3)}**********${value.takeLast(4)}"
    
    override fun toString(): String = value.uppercase()
    
    companion object {
        /** Length of a valid VIN */
        const val VIN_LENGTH = 17
        
        /** Characters that are not allowed in VINs */
        private val INVALID_CHARS = setOf('I', 'O', 'Q')
        
        /**
         * Model year codes and their corresponding years.
         * Cycles every 30 years starting from 2010.
         */
        private val YEAR_CODES = mapOf(
            'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
            'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
            'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
            'S' to 2025, 'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029,
            'Y' to 2030, '1' to 2031, '2' to 2032, '3' to 2033, '4' to 2034,
            '5' to 2035, '6' to 2036, '7' to 2037, '8' to 2038, '9' to 2039
        )
        
        /**
         * Decodes the model year from the year character.
         *
         * @param yearChar The year character (10th position)
         * @return The model year, or null if invalid
         */
        private fun decodeModelYear(yearChar: Char): Int? {
            return YEAR_CODES[yearChar.uppercaseChar()]
        }
        
        /**
         * Creates a VIN if the input is valid, null otherwise.
         *
         * @param vin The VIN string to parse
         * @return VIN if valid, null otherwise
         */
        fun parseOrNull(vin: String): VIN? {
            val normalized = vin.uppercase().trim()
            val candidate = VIN(normalized)
            return if (candidate.isValid()) candidate else null
        }
        
        /**
         * Creates a VIN without validation.
         * Use when you trust the data source.
         *
         * @param vin The VIN string
         * @return VIN instance
         */
        fun fromTrustedSource(vin: String): VIN = VIN(vin.uppercase().trim())
    }
}

// ==================== ENUM CONVERTERS ====================

/**
 * Type converters for all enum types used in SpaceTec entities.
 *
 * All enum conversions use the enum [name] property for maximum
 * stability across app versions. This ensures that reordering enum
 * constants or adding new ones won't corrupt existing data.
 *
 * ## Migration Safety
 * When removing an enum constant, add a migration that handles
 * the orphaned values, or keep the constant marked as @Deprecated.
 */
class EnumConverters {

    // ==================== VEHICLE ENUMS ====================

    @TypeConverter
    fun transmissionTypeToString(type: TransmissionType?): String? = type?.name

    @TypeConverter
    fun stringToTransmissionType(value: String?): TransmissionType? {
        return value?.let {
            try {
                TransmissionType.valueOf(it)
            } catch (e: Exception) {
                TransmissionType.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun fuelTypeToString(type: FuelType?): String? = type?.name

    @TypeConverter
    fun stringToFuelType(value: String?): FuelType? {
        return value?.let {
            try {
                FuelType.valueOf(it)
            } catch (e: Exception) {
                FuelType.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun driveTypeToString(type: DriveType?): String? = type?.name

    @TypeConverter
    fun stringToDriveType(value: String?): DriveType? {
        return value?.let {
            try {
                DriveType.valueOf(it)
            } catch (e: Exception) {
                DriveType.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun mileageUnitToString(unit: MileageUnit?): String? = unit?.name

    @TypeConverter
    fun stringToMileageUnit(value: String?): MileageUnit? {
        return value?.let {
            try {
                MileageUnit.valueOf(it)
            } catch (e: Exception) {
                MileageUnit.KILOMETERS
            }
        }
    }

    // ==================== DTC ENUMS ====================

    @TypeConverter
    fun dtcTypeToString(type: DTCType?): String? = type?.name

    @TypeConverter
    fun stringToDTCType(value: String?): DTCType? {
        return value?.let {
            try {
                DTCType.valueOf(it)
            } catch (e: Exception) {
                DTCType.POWERTRAIN
            }
        }
    }

    @TypeConverter
    fun dtcSeverityToString(severity: DTCSeverity?): String? = severity?.name

    @TypeConverter
    fun stringToDTCSeverity(value: String?): DTCSeverity? {
        return value?.let {
            try {
                DTCSeverity.valueOf(it)
            } catch (e: Exception) {
                DTCSeverity.INFO
            }
        }
    }

    @TypeConverter
    fun repairDifficultyToString(difficulty: RepairDifficulty?): String? = difficulty?.name

    @TypeConverter
    fun stringToRepairDifficulty(value: String?): RepairDifficulty? {
        return value?.let {
            try {
                RepairDifficulty.valueOf(it)
            } catch (e: Exception) {
                RepairDifficulty.MEDIUM
            }
        }
    }

    @TypeConverter
    fun dtcSourceToString(source: DTCSource?): String? = source?.name

    @TypeConverter
    fun stringToDTCSource(value: String?): DTCSource? {
        return value?.let {
            try {
                DTCSource.valueOf(it)
            } catch (e: Exception) {
                DTCSource.AFTERMARKET
            }
        }
    }

    // ==================== SESSION ENUMS ====================

    @TypeConverter
    fun sessionTypeToString(type: SessionType?): String? = type?.name

    @TypeConverter
    fun stringToSessionType(value: String?): SessionType? {
        return value?.let {
            try {
                SessionType.valueOf(it)
            } catch (e: Exception) {
                SessionType.CUSTOM
            }
        }
    }

    @TypeConverter
    fun sessionStatusToString(status: SessionStatus?): String? = status?.name

    @TypeConverter
    fun stringToSessionStatus(value: String?): SessionStatus? {
        return value?.let {
            try {
                SessionStatus.valueOf(it)
            } catch (e: Exception) {
                SessionStatus.FAILED
            }
        }
    }

    @TypeConverter
    fun scanResultToString(result: ScanResult?): String? = result?.name

    @TypeConverter
    fun stringToScanResult(value: String?): ScanResult? {
        return value?.let {
            try {
                ScanResult.valueOf(it)
            } catch (e: Exception) {
                ScanResult.ERROR
            }
        }
    }

    // ==================== PROTOCOL ENUMS ====================

    @TypeConverter
    fun protocolTypeToString(type: ProtocolType?): String? = type?.name

    @TypeConverter
    fun stringToProtocolType(value: String?): ProtocolType? {
        return value?.let {
            try {
                ProtocolType.valueOf(it)
            } catch (e: Exception) {
                ProtocolType.AUTO
            }
        }
    }

    // ==================== ECU ENUMS ====================

    @TypeConverter
    fun ecuTypeToString(type: ECUType?): String? = type?.name

    @TypeConverter
    fun stringToECUType(value: String?): ECUType? {
        return value?.let {
            try {
                ECUType.valueOf(it)
            } catch (e: Exception) {
                ECUType.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun ecuProtocolToString(protocol: ECUProtocol?): String? = protocol?.name

    @TypeConverter
    fun stringToECUProtocol(value: String?): ECUProtocol? {
        return value?.let {
            try {
                ECUProtocol.valueOf(it)
            } catch (e: Exception) {
                ECUProtocol.OBD2
            }
        }
    }

    // ==================== CODING ENUMS ====================

    @TypeConverter
    fun codingTypeToString(type: CodingType?): String? = type?.name

    @TypeConverter
    fun stringToCodingType(value: String?): CodingType? {
        return value?.let {
            try {
                CodingType.valueOf(it)
            } catch (e: Exception) {
                CodingType.SHORT_CODING
            }
        }
    }

    @TypeConverter
    fun valueTypeToString(type: ValueType?): String? = type?.name

    @TypeConverter
    fun stringToValueType(value: String?): ValueType? {
        return value?.let {
            try {
                ValueType.valueOf(it)
            } catch (e: Exception) {
                ValueType.BYTE
            }
        }
    }

    @TypeConverter
    fun codingChangeTypeToString(type: CodingChangeType?): String? = type?.name

    @TypeConverter
    fun stringToCodingChangeType(value: String?): CodingChangeType? {
        return value?.let {
            try {
                CodingChangeType.valueOf(it)
            } catch (e: Exception) {
                CodingChangeType.READ
            }
        }
    }

    // ==================== KEY/IMMO ENUMS ====================

    @TypeConverter
    fun keyTypeToString(type: KeyType?): String? = type?.name

    @TypeConverter
    fun stringToKeyType(value: String?): KeyType? {
        return value?.let {
            try {
                KeyType.valueOf(it)
            } catch (e: Exception) {
                KeyType.TRANSPONDER
            }
        }
    }

    @TypeConverter
    fun keyStatusToString(status: KeyStatus?): String? = status?.name

    @TypeConverter
    fun stringToKeyStatus(value: String?): KeyStatus? {
        return value?.let {
            try {
                KeyStatus.valueOf(it)
            } catch (e: Exception) {
                KeyStatus.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun transponderTypeToString(type: TransponderType?): String? = type?.name

    @TypeConverter
    fun stringToTransponderType(value: String?): TransponderType? {
        return value?.let {
            try {
                TransponderType.valueOf(it)
            } catch (e: Exception) {
                TransponderType.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun batteryStatusToString(status: BatteryStatus?): String? = status?.name

    @TypeConverter
    fun stringToBatteryStatus(value: String?): BatteryStatus? {
        return value?.let {
            try {
                BatteryStatus.valueOf(it)
            } catch (e: Exception) {
                BatteryStatus.UNKNOWN
            }
        }
    }

    @TypeConverter
    fun programmingMethodToString(method: ProgrammingMethod?): String? = method?.name

    @TypeConverter
    fun stringToProgrammingMethod(value: String?): ProgrammingMethod? {
        return value?.let {
            try {
                ProgrammingMethod.valueOf(it)
            } catch (e: Exception) {
                ProgrammingMethod.ONBOARD
            }
        }
    }

    @TypeConverter
    fun keySessionTypeToString(type: KeySessionType?): String? = type?.name

    @TypeConverter
    fun stringToKeySessionType(value: String?): KeySessionType? {
        return value?.let {
            try {
                KeySessionType.valueOf(it)
            } catch (e: Exception) {
                KeySessionType.ADD_KEY
            }
        }
    }

    @TypeConverter
    fun pinSourceToString(source: PinSource?): String? = source?.name

    @TypeConverter
    fun stringToPinSource(value: String?): PinSource? {
        return value?.let {
            try {
                PinSource.valueOf(it)
            } catch (e: Exception) {
                PinSource.ENTERED_BY_USER
            }
        }
    }

    @TypeConverter
    fun immobilizerTypeToString(type: ImmobilizerType?): String? = type?.name

    @TypeConverter
    fun stringToImmobilizerType(value: String?): ImmobilizerType? {
        return value?.let {
            try {
                ImmobilizerType.valueOf(it)
            } catch (e: Exception) {
                ImmobilizerType.UNKNOWN
            }
        }
    }

    // ==================== SCANNER ENUMS ====================

    @TypeConverter
    fun scannerDeviceTypeToString(type: ScannerDeviceType?): String? = type?.name

    @TypeConverter
    fun stringToScannerDeviceType(value: String?): ScannerDeviceType? {
        return value?.let {
            try {
                ScannerDeviceType.valueOf(it)
            } catch (e: Exception) {
                ScannerDeviceType.GENERIC
            }
        }
    }

    @TypeConverter
    fun connectionTypeToString(type: ConnectionType?): String? = type?.name

    @TypeConverter
    fun stringToConnectionType(value: String?): ConnectionType? {
        return value?.let {
            try {
                ConnectionType.valueOf(it)
            } catch (e: Exception) {
                ConnectionType.BLUETOOTH_CLASSIC
            }
        }
    }

    @TypeConverter
    fun connectionErrorTypeToString(type: ConnectionErrorType?): String? = type?.name

    @TypeConverter
    fun stringToConnectionErrorType(value: String?): ConnectionErrorType? {
        return value?.let {
            try {
                ConnectionErrorType.valueOf(it)
            } catch (e: Exception) {
                ConnectionErrorType.UNKNOWN
            }
        }
    }

    // ==================== LIVE DATA ENUMS ====================

    @TypeConverter
    fun triggerTypeToString(type: TriggerType?): String? = type?.name

    @TypeConverter
    fun stringToTriggerType(value: String?): TriggerType? {
        return value?.let {
            try {
                TriggerType.valueOf(it)
            } catch (e: Exception) {
                TriggerType.MANUAL
            }
        }
    }

    @TypeConverter
    fun logFileFormatToString(format: LogFileFormat?): String? = format?.name

    @TypeConverter
    fun stringToLogFileFormat(value: String?): LogFileFormat? {
        return value?.let {
            try {
                LogFileFormat.valueOf(it)
            } catch (e: Exception) {
                LogFileFormat.INTERNAL
            }
        }
    }

    // ==================== SYNC ENUMS ====================

    @TypeConverter
    fun syncStatusToString(status: SyncStatus?): String? = status?.name

    @TypeConverter
    fun stringToSyncStatus(value: String?): SyncStatus? {
        return value?.let {
            try {
                SyncStatus.valueOf(it)
            } catch (e: Exception) {
                SyncStatus.LOCAL
            }
        }
    }
}

// ==================== ENUM DEFINITIONS ====================

/**
 * Vehicle transmission types.
 */
enum class TransmissionType {
    /** Traditional automatic transmission with torque converter */
    AUTOMATIC,
    /** Manual/stick shift transmission */
    MANUAL,
    /** Continuously Variable Transmission */
    CVT,
    /** Dual Clutch Transmission (DCT/DSG/PDK) */
    DCT,
    /** Automated Manual (single clutch, e.g., SMG) */
    AUTOMATED_MANUAL,
    /** Electric vehicle (no traditional transmission) */
    ELECTRIC_SINGLE_SPEED,
    /** Unknown transmission type */
    UNKNOWN
}

/**
 * Vehicle fuel/energy types.
 */
enum class FuelType {
    /** Gasoline/Petrol */
    GASOLINE,
    /** Diesel */
    DIESEL,
    /** Pure electric vehicle */
    ELECTRIC,
    /** Hybrid with gasoline engine */
    HYBRID_PETROL,
    /** Hybrid with diesel engine */
    HYBRID_DIESEL,
    /** Plug-in hybrid */
    PLUGIN_HYBRID,
    /** Hydrogen fuel cell */
    HYDROGEN,
    /** Liquefied Petroleum Gas */
    LPG,
    /** Compressed Natural Gas */
    CNG,
    /** Flex fuel (E85 compatible) */
    FLEX_FUEL,
    /** Unknown fuel type */
    UNKNOWN
}

/**
 * Vehicle drivetrain types.
 */
enum class DriveType {
    /** Front-wheel drive */
    FWD,
    /** Rear-wheel drive */
    RWD,
    /** All-wheel drive (permanent or automatic) */
    AWD,
    /** Four-wheel drive (part-time, selectable) */
    FOUR_WD,
    /** Unknown drive type */
    UNKNOWN
}

/**
 * Units for mileage/odometer readings.
 */
enum class MileageUnit {
    KILOMETERS,
    MILES
}

/**
 * DTC system categories following SAE J2012.
 */
enum class DTCType {
    /** P codes - Engine, transmission, fuel system */
    POWERTRAIN,
    /** C codes - ABS, steering, suspension */
    CHASSIS,
    /** B codes - Airbag, HVAC, lighting */
    BODY,
    /** U codes - CAN bus, communication */
    NETWORK,
    /** Manufacturer-specific codes outside standard ranges */
    MANUFACTURER_SPECIFIC
}

/**
 * Severity level of a diagnostic trouble code.
 */
enum class DTCSeverity {
    /** Informational, may not indicate a problem */
    INFO,
    /** Warning, should be investigated */
    WARNING,
    /** Critical, immediate attention required */
    CRITICAL
}

/**
 * Difficulty level for repairing a DTC.
 */
enum class RepairDifficulty {
    /** Can be done by car owner */
    EASY,
    /** Requires some automotive knowledge */
    MEDIUM,
    /** Professional mechanic recommended */
    HARD,
    /** Dealer-only with special tools/access */
    DEALER
}

/**
 * Source of DTC definition data.
 */
enum class DTCSource {
    /** Official OEM documentation */
    OEM,
    /** Aftermarket database (AllData, Mitchell, etc.) */
    AFTERMARKET,
    /** Community-contributed definition */
    COMMUNITY,
    /** SpaceTec internal database */
    SPACETEC
}

/**
 * Types of diagnostic sessions.
 */
enum class SessionType {
    /** Quick scan - reads DTCs only */
    QUICK_SCAN,
    /** Full system scan - all ECUs */
    FULL_SYSTEM_SCAN,
    /** Single ECU scan */
    SINGLE_ECU_SCAN,
    /** Live data recording session */
    LIVE_DATA,
    /** Coding/programming session */
    CODING,
    /** Actuator/output test session */
    ACTUATOR_TEST,
    /** Key programming session */
    KEY_PROGRAMMING,
    /** Service/maintenance reset */
    MAINTENANCE,
    /** Custom/user-defined session */
    CUSTOM
}

/**
 * Status of a diagnostic session.
 */
enum class SessionStatus {
    /** Session is currently in progress */
    IN_PROGRESS,
    /** Session completed successfully */
    COMPLETED,
    /** Session failed with error */
    FAILED,
    /** Session was cancelled by user */
    CANCELLED,
    /** Session completed partially */
    PARTIAL
}

/**
 * Result of scanning an individual ECU.
 */
enum class ScanResult {
    /** ECU scanned successfully */
    SUCCESS,
    /** ECU responded but with errors */
    ERROR,
    /** ECU did not respond */
    NO_RESPONSE,
    /** ECU scan was partial/incomplete */
    PARTIAL,
    /** ECU was skipped */
    SKIPPED
}

/**
 * Communication protocols for vehicle diagnostics.
 */
enum class ProtocolType {
    /** Automatic protocol detection */
    AUTO,
    /** ISO 9141-2 (slow init) */
    ISO_9141,
    /** ISO 14230-4 KWP2000 (5-baud or fast init) */
    ISO_14230_KWP2000,
    /** ISO 15765-4 CAN 11-bit 500kbaud */
    ISO_15765_CAN_11BIT_500K,
    /** ISO 15765-4 CAN 29-bit 500kbaud */
    ISO_15765_CAN_29BIT_500K,
    /** ISO 15765-4 CAN 11-bit 250kbaud */
    ISO_15765_CAN_11BIT_250K,
    /** ISO 15765-4 CAN 29-bit 250kbaud */
    ISO_15765_CAN_29BIT_250K,
    /** SAE J1850 VPW (GM) */
    J1850_VPW,
    /** SAE J1850 PWM (Ford) */
    J1850_PWM,
    /** Single Wire CAN (GM) */
    SINGLE_WIRE_CAN,
    /** Medium Speed CAN (Ford) */
    FORD_MSCAN,
    /** UDS over CAN (ISO 14229) */
    UDS,
    /** Manufacturer-specific protocol */
    MANUFACTURER_SPECIFIC,
    /** Unknown protocol */
    UNKNOWN
}

/**
 * Types of Electronic Control Units.
 */
enum class ECUType {
    /** Engine Control Module / Powertrain Control Module */
    ENGINE,
    /** Transmission Control Module */
    TRANSMISSION,
    /** Anti-lock Braking System */
    ABS,
    /** Airbag / Supplemental Restraint System */
    AIRBAG,
    /** Body Control Module */
    BODY_CONTROL,
    /** Instrument Cluster */
    INSTRUMENT,
    /** Heating, Ventilation, Air Conditioning */
    HVAC,
    /** Power Steering Module */
    STEERING,
    /** Central Gateway Module */
    GATEWAY,
    /** Head Unit / Infotainment */
    INFOTAINMENT,
    /** Tire Pressure Monitoring System */
    TPMS,
    /** Parking Assist / PDC */
    PARK_ASSIST,
    /** Camera systems (rear view, 360, etc.) */
    CAMERA,
    /** Air Suspension / Adaptive Suspension */
    SUSPENSION,
    /** Lighting Control Module */
    LIGHTING,
    /** Door modules */
    DOORS,
    /** Seat Control Module */
    SEATS,
    /** Battery Management System (EV/Hybrid) */
    BATTERY_MANAGEMENT,
    /** Hybrid/EV powertrain controller */
    HYBRID,
    /** Convenience/Comfort module */
    CONVENIENCE,
    /** Access/Entry module */
    ACCESS,
    /** Unknown ECU type */
    UNKNOWN,
    /** Custom/user-defined ECU type */
    CUSTOM
}

/**
 * Protocol used by specific ECU for diagnostics.
 */
enum class ECUProtocol {
    /** Standard OBD-II protocol */
    OBD2,
    /** Unified Diagnostic Services (ISO 14229) */
    UDS,
    /** KWP2000 (Keyword Protocol) */
    KWP2000,
    /** Manufacturer-specific protocol */
    MANUFACTURER_SPECIFIC,
    /** Unknown protocol */
    UNKNOWN
}

/**
 * Types of ECU coding/adaptation.
 */
enum class CodingType {
    /** Short coding - single numeric value */
    SHORT_CODING,
    /** Long coding - byte array */
    LONG_CODING,
    /** Adaptation channel values */
    ADAPTATION,
    /** Parametric coding (newer systems) */
    PARAMETRIC,
    /** Online/SVM coding (requires server) */
    ONLINE
}

/**
 * Data type for coding values.
 */
enum class ValueType {
    /** Single byte (0-255) */
    BYTE,
    /** Two bytes (0-65535) */
    WORD,
    /** Four bytes */
    DWORD,
    /** Arbitrary byte array */
    BYTE_ARRAY,
    /** Single bit within a byte */
    BIT,
    /** ASCII/text string */
    STRING,
    /** Floating point number */
    FLOAT,
    /** Boolean true/false */
    BOOLEAN
}

/**
 * Type of coding change operation.
 */
enum class CodingChangeType {
    /** Read current value from ECU */
    READ,
    /** Write new value to ECU */
    WRITE,
    /** Reset to factory default */
    RESET,
    /** Transfer from backup */
    RESTORE
}

/**
 * Types of vehicle keys.
 */
enum class KeyType {
    /** Mechanical key only (no electronics) */
    MECHANICAL,
    /** Transponder key (chipped key for immobilizer) */
    TRANSPONDER,
    /** Remote with built-in transponder */
    REMOTE_TRANSPONDER,
    /** Smart key / Keyless entry and start (PKES) */
    SMART_KEY,
    /** Emergency/backup key */
    EMERGENCY,
    /** Valet key (limited functionality) */
    VALET,
    /** Service/workshop key */
    SERVICE,
    /** Dealer/master key */
    DEALER
}

/**
 * Status of a vehicle key.
 */
enum class KeyStatus {
    /** Key is active and working */
    ACTIVE,
    /** Key is programmed but deactivated */
    INACTIVE,
    /** Key is marked as lost */
    LOST,
    /** Key has been deleted from system */
    DELETED,
    /** Key status is unknown */
    UNKNOWN,
    /** Spare/backup key */
    SPARE
}

/**
 * Types of key transponders.
 */
enum class TransponderType {
    /** Fixed code transponder */
    FIXED_CODE,
    /** Generic crypto transponder */
    CRYPTO,
    /** Philips PCF7935 */
    PCF7935,
    /** Philips PCF7936 (Hitag2) */
    PCF7936_HITAG2,
    /** Philips PCF7945 */
    PCF7945,
    /** Philips PCF7953 */
    PCF7953,
    /** Megamos AES */
    MEGAMOS_AES,
    /** Texas Instruments DST80 */
    DST80,
    /** ID46 (Hitag2 based) */
    ID46,
    /** ID48 (Megamos Crypto) */
    ID48,
    /** ID49 (Hitag-AES Pro) */
    ID49,
    /** ID4A (Hitag-AES) */
    ID4A,
    /** ID8A (Hitag-AES Pro) */
    ID8A,
    /** Unknown transponder type */
    UNKNOWN,
    /** Other/specialized transponder */
    OTHER
}

/**
 * Battery status for remote key fobs.
 */
enum class BatteryStatus {
    /** Battery is good */
    GOOD,
    /** Battery is low, replacement recommended */
    LOW,
    /** Battery status is unknown */
    UNKNOWN
}

/**
 * Methods for programming keys.
 */
enum class ProgrammingMethod {
    /** On-board programming via OBD port */
    ONBOARD,
    /** Off-board programming (EEPROM, etc.) */
    OFFBOARD,
    /** Dealer-only online programming */
    DEALER,
    /** Direct EEPROM manipulation */
    EEPROM,
    /** Hybrid method (combination) */
    HYBRID
}

/**
 * Types of key programming sessions.
 */
enum class KeySessionType {
    /** Adding a new key */
    ADD_KEY,
    /** Deleting a specific key */
    DELETE_KEY,
    /** Deleting all keys */
    DELETE_ALL_KEYS,
    /** Learning/synchronizing a key */
    LEARN_KEY,
    /** Replacing a key */
    REPLACE_KEY,
    /** All keys lost procedure */
    ALL_KEYS_LOST,
    /** Synchronizing key with ECU */
    SYNC_KEY,
    /** Resetting immobilizer */
    RESET_IMMOBILIZER
}

/**
 * Source of PIN/Security code.
 */
enum class PinSource {
    /** PIN entered manually by user */
    ENTERED_BY_USER,
    /** PIN calculated from EEPROM dump */
    CALCULATED_FROM_DUMP,
    /** PIN read directly from BCM/ECU */
    READ_FROM_BCM,
    /** PIN obtained from dealer */
    DEALER,
    /** PIN from online service */
    ONLINE_SERVICE
}

/**
 * Types of vehicle immobilizer systems.
 */
enum class ImmobilizerType {
    /** No immobilizer or unknown */
    UNKNOWN,
    /** First generation immobilizer */
    IMMO1,
    /** Second generation immobilizer */
    IMMO2,
    /** Third generation immobilizer */
    IMMO3,
    /** Fourth generation immobilizer */
    IMMO4,
    /** Fifth generation immobilizer */
    IMMO5,
    /** Bosch MED17 based */
    MED17,
    /** Siemens/Continental based */
    SIMOS,
    /** UDS-based immobilizer */
    UDS,
    /** Component security (dealer only) */
    COMPONENT_SECURITY,
    /** Online authorization required */
    ONLINE_AUTH
}

/**
 * Types of OBD scanner devices.
 */
enum class ScannerDeviceType {
    /** Generic/clone ELM327 */
    ELM327_CLONE,
    /** Genuine ELM327 chip */
    ELM327_GENUINE,
    /** OBDLink MX / MX+ */
    OBDLINK_MX,
    /** OBDLink EX */
    OBDLINK_EX,
    /** OBDLink SX */
    OBDLINK_SX,
    /** OBDLink LX */
    OBDLINK_LX,
    /** OBDLink CX */
    OBDLINK_CX,
    /** Vgate iCar series */
    VGATE,
    /** Veepeak adapters */
    VEEPEAK,
    /** Carista OBD2 adapter */
    CARISTA,
    /** BlueDriver */
    BLUE_DRIVER,
    /** Tactrix OpenPort */
    TACTRIX,
    /** Drew Technologies (CarDAQ, Mongoose) */
    DREW_TECH,
    /** Autel devices */
    AUTEL,
    /** Launch devices */
    LAUNCH,
    /** VCDS HEX-NET / HEX-V2 */
    VCDS,
    /** Generic J2534 device */
    J2534_DEVICE,
    /** Other/generic device */
    GENERIC,
    /** Custom/proprietary device */
    CUSTOM
}

/**
 * Connection types for scanner devices.
 */
enum class ConnectionType {
    /** Bluetooth Classic (SPP) */
    BLUETOOTH_CLASSIC,
    /** Bluetooth Low Energy */
    BLUETOOTH_LE,
    /** WiFi connection */
    WIFI,
    /** USB connection */
    USB,
    /** WiFi Direct */
    WIFI_DIRECT
}

/**
 * Types of connection errors for scanner devices.
 */
enum class ConnectionErrorType {
    /** Connection timed out */
    TIMEOUT,
    /** Device not found */
    NOT_FOUND,
    /** Bluetooth pairing failed */
    PAIRING_FAILED,
    /** Connection refused by device */
    REFUSED,
    /** Socket/connection error */
    SOCKET_ERROR,
    /** Protocol error during connection */
    PROTOCOL_ERROR,
    /** Insufficient permissions */
    PERMISSION_DENIED,
    /** Device is already connected elsewhere */
    DEVICE_BUSY,
    /** Signal too weak */
    WEAK_SIGNAL,
    /** Unknown error */
    UNKNOWN
}

/**
 * Trigger types for live data recording.
 */
enum class TriggerType {
    /** Manual start/stop */
    MANUAL,
    /** Triggered by DTC occurrence */
    DTC,
    /** Triggered by value threshold */
    THRESHOLD,
    /** Scheduled recording */
    SCHEDULED,
    /** Triggered by ignition on/off */
    IGNITION,
    /** Triggered by vehicle speed */
    SPEED,
    /** Triggered by RPM threshold */
    RPM,
    /** Triggered by GPS location */
    GEOFENCE
}

/**
 * File formats for exported log data.
 */
enum class LogFileFormat {
    /** Internal binary format */
    INTERNAL,
    /** Comma-separated values */
    CSV,
    /** Microsoft Excel format */
    EXCEL,
    /** JSON format */
    JSON,
    /** SQLite database export */
    SQLITE
}

/**
 * Sync status for cloud synchronization.
 */
enum class SyncStatus {
    /** Only stored locally, never synced */
    LOCAL,
    /** Synced with cloud */
    SYNCED,
    /** Changed locally, pending sync */
    PENDING_SYNC,
    /** Conflict between local and cloud versions */
    CONFLICT
}	
