/**
 * SpaceTecException.kt
 *
 * Base exception class for all SpaceTec application exceptions.
 * This serves as the root exception class for the SpaceTec diagnostic application.
 */

package com.spacetec.core.common.exceptions

/**
 * Base exception class for all SpaceTec application exceptions.
 * All other exceptions in the application should inherit from this class.
 */
abstract class SpaceTecException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)