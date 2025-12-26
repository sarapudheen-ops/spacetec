package com.obdreader.domain.model

/**
 * Represents a parsed PID response from the vehicle.
 */
data class PIDResponse(
    val mode: Int,
    val pid: Int,
    val name: String,
    val rawData: ByteArray,
    val value: Double,
    val unit: String,
    val minValue: Double,
    val maxValue: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val ecuAddress: String? = null
) {
    /**
     * Get formatted value with unit.
     */
    fun getFormattedValue(decimalPlaces: Int = 1): String {
        return "%.${decimalPlaces}f $unit".format(value)
    }
    
    /**
     * Get value as percentage of max range.
     */
    fun getPercentageOfRange(): Double {
        val range = maxValue - minValue
        return if (range > 0) ((value - minValue) / range) * 100 else 0.0
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PIDResponse
        return mode == other.mode && pid == other.pid && 
               rawData.contentEquals(other.rawData) && 
               timestamp == other.timestamp
    }
    
    override fun hashCode(): Int {
        var result = mode
        result = 31 * result + pid
        result = 31 * result + rawData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
    
    companion object {
        /**
         * Create a "no data" response for unsupported PIDs.
         */
        fun noData(mode: Int, pid: Int, name: String = "Unknown"): PIDResponse {
            return PIDResponse(
                mode = mode,
                pid = pid,
                name = name,
                rawData = byteArrayOf(),
                value = Double.NaN,
                unit = "",
                minValue = 0.0,
                maxValue = 0.0
            )
        }
    }
}
