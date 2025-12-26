/**
 * CommunicationException.kt
 *
 * Exception thrown when communication with the vehicle fails.
 */

package com.spacetec.core.common.exceptions

/**
 * Exception thrown when communication with the vehicle fails.
 */
class CommunicationException(
    message: String,
    cause: Throwable? = null
) : SpaceTecException(message, cause)