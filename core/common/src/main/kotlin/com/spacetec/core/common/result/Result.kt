package com.spacetec.obd.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * A sealed class representing the result of an operation that can either
 * succeed with a value of type [T] or fail with an error of type [E].
 * 
 * This is a functional programming pattern commonly used for error handling
 * without exceptions, providing a more explicit and type-safe approach.
 * 
 * @param T The type of the success value
 * @param E The type of the error value
 */
sealed class Result<out T, out E> {
    
    /**
     * Represents a successful result containing a value.
     * 
     * @param value The success value
     */
    data class Success<out T>(val value: T) : Result<T, Nothing>()
    
    /**
     * Represents a failed result containing an error.
     * 
     * @param error The error value
     */
    data class Failure<out E>(val error: E) : Result<Nothing, E>()
    
    /**
     * Returns true if this is a Success, false otherwise.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is a Failure, false otherwise.
     */
    val isFailure: Boolean get() = this is Failure
    
    /**
     * Returns the success value or null if this is a Failure.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }
    
    /**
     * Returns the error value or null if this is a Success.
     */
    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }
    
    /**
     * Returns the success value or the result of [defaultValue] if this is a Failure.
     */
    inline fun getOrElse(defaultValue: () -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> defaultValue()
    }
    
    /**
     * Returns the success value or throws the error if this is a Failure.
     * Note: The error must be a Throwable for this to work.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw (error as? Throwable ?: IllegalStateException("Result is Failure: $error"))
    }
    
    /**
     * Transforms the success value using the given [transform] function.
     */
    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    
    /**
     * Transforms the error value using the given [transform] function.
     */
    inline fun <R> mapError(transform: (E) -> R): Result<T, R> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }
    
    /**
     * Transforms the success value using the given [transform] function
     * that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
    
    /**
     * Transforms the error value using the given [transform] function
     * that returns a Result.
     */
    inline fun <R> flatMapError(transform: (E) -> Result<@UnsafeVariance T, R>): Result<T, R> = when (this) {
        is Success -> this
        is Failure -> transform(error)
    }
    
    /**
     * Executes [action] if this is a Success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (this is Success) action(value)
        return this
    }
    
    /**
     * Executes [action] if this is a Failure.
     */
    inline fun onFailure(action: (E) -> Unit): Result<T, E> {
        if (this is Failure) action(error)
        return this
    }
    
    /**
     * Folds the result into a single value by applying [onSuccess] or [onFailure].
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (E) -> R
    ): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(error)
    }
    
    /**
     * Recovers from a failure by returning a default value.
     */
    inline fun recover(recovery: (E) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> recovery(error)
    }
    
    /**
     * Recovers from a failure by returning a new Result.
     */
    inline fun recoverWith(recovery: (E) -> Result<@UnsafeVariance T, @UnsafeVariance E>): Result<T, E> = when (this) {
        is Success -> this
        is Failure -> recovery(error)
    }
    
    companion object {
        /**
         * Creates a Success result with the given value.
         */
        fun <T> success(value: T): Result<T, Nothing> = Success(value)
        
        /**
         * Creates a Failure result with the given error.
         */
        fun <E> failure(error: E): Result<Nothing, E> = Failure(error)
        
        /**
         * Creates a Failure result with the given error (alias for failure).
         */
        fun <E> error(error: E): Result<Nothing, E> = Failure(error)
        
        /**
         * Runs the given [block] and wraps the result in a Result,
         * catching any exceptions as Failure.
         */
        inline fun <T> runCatching(block: () -> T): Result<T, Throwable> = try {
            Success(block())
        } catch (e: Throwable) {
            Failure(e)
        }
        
        /**
         * Combines two Results into a pair if both are successful.
         */
        fun <A, B, E> zip(
            first: Result<A, E>,
            second: Result<B, E>
        ): Result<Pair<A, B>, E> = first.flatMap { a ->
            second.map { b -> a to b }
        }
        
        /**
         * Combines three Results into a Triple if all are successful.
         */
        fun <A, B, C, E> zip(
            first: Result<A, E>,
            second: Result<B, E>,
            third: Result<C, E>
        ): Result<Triple<A, B, C>, E> = first.flatMap { a ->
            second.flatMap { b ->
                third.map { c -> Triple(a, b, c) }
            }
        }
    }
}

/**
 * Type alias for Result with SpaceTecError as error type.
 */
typealias AppResult<T> = Result<T, SpaceTecError>

/**
 * Converts a nullable value to a Result.
 */
fun <T, E> T?.toResult(error: () -> E): Result<T, E> =
    if (this != null) Result.success(this) else Result.failure(error())

/**
 * Converts a Kotlin Result to our Result type.
 */
fun <T> kotlin.Result<T>.toResult(): Result<T, Throwable> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it) }
    )

/**
 * Extension to convert Flow<T> to Flow<Result<T, Throwable>>
 */
fun <T> Flow<T>.asResultFlow(): Flow<Result<T, Throwable>> = this
    .map<T, Result<T, Throwable>> { Result.success(it) }
    .catch { emit(Result.failure(it)) }