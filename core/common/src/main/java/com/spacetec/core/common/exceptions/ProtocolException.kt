/**
 * ProtocolException.kt
 *
 * Exception thrown when protocol-related errors occur.
 */

package com.spacetec.core.common.exceptions

/**
 * Exception thrown when protocol-related errors occur.
 */
class ProtocolException(
    message: String,
    cause: Throwable? = null
) : SpaceTecException(message, cause)