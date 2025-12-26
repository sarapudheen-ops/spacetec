/**
 * TimeoutException.kt
 *
 * Exception thrown when a timeout occurs during communication or operation.
 */

package com.spacetec.core.common.exceptions

/**
 * Exception thrown when a timeout occurs during communication or operation.
 */
class TimeoutException(
    message: String,
    cause: Throwable? = null
) : SpaceTecException(message, cause)