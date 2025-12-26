package com.obdreader.domain.models

/**
 * Freeze frame data captured when a DTC was set
 */
data class FreezeFrame(
    val frameNumber: Int,
    val triggerDTC: String?,
    val data: List<com.obdreader.domain.model.PIDResponse>,
    val timestamp: Long
)
