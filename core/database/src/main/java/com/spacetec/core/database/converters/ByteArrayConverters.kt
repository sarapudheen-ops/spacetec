package com.spacetec.obd.core.database.converters

import androidx.room.TypeConverter
import android.util.Base64

/**
 * Room type converters for byte array types
 */
class ByteArrayConverters {
    
    @TypeConverter
    fun fromByteArray(byteArray: ByteArray?): String? {
        return byteArray?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }
    
    @TypeConverter
    fun toByteArray(encodedString: String?): ByteArray? {
        return encodedString?.let { Base64.decode(it, Base64.DEFAULT) }
    }
}