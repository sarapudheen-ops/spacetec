package com.spacetec.obd.core.domain.model

/**
 * Minimal DTC representation kept in `core:domain`.
 *
 * NOTE: Higher-level and richer DTC models exist elsewhere in the codebase; this model exists
 * to keep `core:domain` buildable when protocol/scanner modules are disabled.
 */
data class DTC(
    val code: String,
    val description: String = ""
)
