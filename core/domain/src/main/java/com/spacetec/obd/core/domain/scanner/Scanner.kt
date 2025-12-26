package com.spacetec.obd.core.domain.scanner

/**
 * Scanner abstraction used by domain use-cases.
 *
 * The full scanner implementation lives in scanner modules which may be disabled in some builds.
 */
interface Scanner {
    suspend fun send(command: ByteArray): ByteArray?
}
