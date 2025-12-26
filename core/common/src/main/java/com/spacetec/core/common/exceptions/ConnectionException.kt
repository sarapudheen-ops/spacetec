/**
 * ConnectionException.kt
 *
 * Exception thrown when connection-related errors occur.
 */

package com.spacetec.core.common.exceptions

/**
 * Exception thrown when connection-related errors occur.
 */
class ConnectionException(
    message: String,
    cause: Throwable? = null
) : SpaceTecException(message, cause)