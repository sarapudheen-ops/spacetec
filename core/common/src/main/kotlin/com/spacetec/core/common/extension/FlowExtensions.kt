package com.spacetec.obd.core.common.extension

import com.spacetec.obd.core.common.result.Result
import com.spacetec.obd.core.common.result.SpaceTecError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Extension functions for Kotlin Flow.
 * 
 * These utilities provide common flow operations used throughout
 * the SpaceTec application for reactive data handling.
 */

/**
 * Wraps flow emissions in Result, catching any errors.
 */
fun <T> Flow<T>.asResult(): Flow<Result<T, SpaceTecError>> = this
    .map<T, Result<T, SpaceTecError>> { Result.success(it) }
    .catch { throwable ->
        emit(Result.failure(SpaceTecError.fromThrowable(throwable)))
    }

/**
 * Catches errors and converts them to SpaceTecError.
 */
fun <T> Flow<T>.catchAsError(
    onError: suspend (SpaceTecError) -> Unit = {}
): Flow<T> = catch { throwable ->
    val error = SpaceTecError.fromThrowable(throwable)
    onError(error)
    Timber.e(throwable, "Flow error: ${error.code}")
}

/**
 * Logs each emission for debugging.
 */
fun <T> Flow<T>.logEmissions(tag: String = "Flow"): Flow<T> = onEach { value ->
    Timber.d("[$tag] Emitted: $value")
}.onStart {
    Timber.d("[$tag] Started")
}.onCompletion { cause ->
    if (cause != null) {
        Timber.e("[$tag] Completed with error: $cause")
    } else {
        Timber.d("[$tag] Completed successfully")
    }
}

/**
 * Retries the flow with exponential backoff on failure.
 * 
 * @param maxRetries Maximum number of retry attempts
 * @param initialDelay Initial delay before first retry
 * @param maxDelay Maximum delay between retries
 * @param factor Multiplier for exponential backoff
 * @param shouldRetry Predicate to determine if retry should occur
 */
fun <T> Flow<T>.retryWithBackoff(
    maxRetries: Int = 3,
    initialDelay: Duration = 1.seconds,
    maxDelay: Duration = 30.seconds,
    factor: Double = 2.0,
    shouldRetry: (Throwable) -> Boolean = { true }
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt >= maxRetries || !shouldRetry(cause)) {
        Timber.w("Flow retry exhausted after $attempt attempts")
        false
    } else {
        val delayMs = (initialDelay.inWholeMilliseconds * Math.pow(factor, attempt.toDouble()))
            .toLong()
            .coerceAtMost(maxDelay.inWholeMilliseconds)
        
        Timber.d("Flow retry attempt ${attempt + 1}/$maxRetries, waiting ${delayMs}ms")
        delay(delayMs)
        true
    }
}

/**
 * Throttles emissions to only emit at most once per specified duration.
 */
fun <T> Flow<T>.throttleFirst(duration: Duration): Flow<T> = flow {
    var lastEmissionTime = 0L
    collect { value ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEmissionTime >= duration.inWholeMilliseconds) {
            lastEmissionTime = currentTime
            emit(value)
        }
    }
}

/**
 * Debounces emissions, only emitting after a pause in emissions.
 */
fun <T> Flow<T>.debounce(duration: Duration): Flow<T> = flow {
    var lastValue: T? = null
    var lastEmissionTime = 0L
    
    collect { value ->
        lastValue = value
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastEmissionTime >= duration.inWholeMilliseconds) {
            delay(duration)
            lastValue?.let {
                emit(it)
                lastEmissionTime = System.currentTimeMillis()
            }
        }
    }
}

/**
 * Takes emissions until a condition is met (inclusive).
 */
fun <T> Flow<T>.takeUntilInclusive(predicate: suspend (T) -> Boolean): Flow<T> = 
    transformWhile { value ->
        emit(value)
        !predicate(value)
    }

/**
 * Takes emissions while a condition is met.
 */
fun <T> Flow<T>.takeWhileInclusive(predicate: suspend (T) -> Boolean): Flow<T> = 
    transformWhile { value ->
        val shouldContinue = predicate(value)
        emit(value)
        shouldContinue
    }

/**
 * Times out if no emission occurs within the specified duration.
 */
fun <T> Flow<T>.timeout(duration: Duration): Flow<T> = flow {
    kotlinx.coroutines.withTimeout(duration) {
        collect { emit(it) }
    }
}

/**
 * Executes the flow on the IO dispatcher.
 */
fun <T> Flow<T>.onIO(): Flow<T> = flowOn(Dispatchers.IO)

/**
 * Executes the flow on the Default dispatcher.
 */
fun <T> Flow<T>.onDefault(): Flow<T> = flowOn(Dispatchers.Default)

/**
 * Executes the flow on the Main dispatcher.
 */
fun <T> Flow<T>.onMain(): Flow<T> = flowOn(Dispatchers.Main)

/**
 * Collects the first emission that matches the predicate.
 */
suspend fun <T> Flow<T>.firstOrNull(predicate: suspend (T) -> Boolean): T? = try {
    first(predicate)
} catch (e: NoSuchElementException) {
    null
}

/**
 * Combines multiple flows and emits when any of them emit.
 */
fun <T> combineFlows(vararg flows: Flow<T>): Flow<T> = flow {
    for (f in flows) {
        emitAll(f)
    }
}

/**
 * Maps each value with a suspending transformer, keeping only non-null results.
 */
fun <T, R : Any> Flow<T>.mapNotNull(transform: suspend (T) -> R?): Flow<R> = 
    map(transform).filterNotNull()

/**
 * Filters out null values from the flow.
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> Flow<T?>.filterNotNull(): Flow<T> = 
    map { it ?: return@map null }
        .let { it as Flow<T> }

/**
 * Emits a value periodically.
 */
fun <T> tickerFlow(period: Duration, initialDelay: Duration = 0.milliseconds, value: () -> T): Flow<T> = 
    flow {
        delay(initialDelay)
        while (true) {
            emit(value())
            delay(period)
        }
    }

/**
 * Creates a simple countdown flow.
 */
fun countdownFlow(from: Int, period: Duration = 1.seconds): Flow<Int> = flow {
    for (i in from downTo 0) {
        emit(i)
        if (i > 0) delay(period)
    }
}

/**
 * Samples the latest value at regular intervals.
 */
fun <T> Flow<T>.sample(period: Duration): Flow<T> = flow {
    var latestValue: T? = null
    var hasValue = false
    
    kotlinx.coroutines.coroutineScope {
        launch {
            collect { value ->
                latestValue = value
                hasValue = true
            }
        }
        
        while (true) {
            delay(period)
            if (hasValue) {
                latestValue?.let { emit(it) }
            }
        }
    }
}

/**
 * Chunks emissions into lists of specified size.
 */
fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
    require(size > 0) { "Chunk size must be positive" }
    
    val buffer = mutableListOf<T>()
    collect { value ->
        buffer.add(value)
        if (buffer.size >= size) {
            emit(buffer.toList())
            buffer.clear()
        }
    }
    if (buffer.isNotEmpty()) {
        emit(buffer.toList())
    }
}

/**
 * Adds an index to each emission.
 */
fun <T> Flow<T>.withIndex(): Flow<IndexedValue<T>> = flow {
    var index = 0
    collect { value ->
        emit(IndexedValue(index++, value))
    }
}

/**
 * Emits pairs of consecutive values.
 */
fun <T> Flow<T>.pairwise(): Flow<Pair<T, T>> = flow {
    var previous: T? = null
    var hasPrevious = false
    
    collect { current ->
        if (hasPrevious) {
            @Suppress("UNCHECKED_CAST")
            emit(previous as T to current)
        }
        previous = current
        hasPrevious = true
    }
}

/**
 * Distinct until a key changes.
 */
fun <T, K> Flow<T>.distinctUntilChangedBy(keySelector: (T) -> K): Flow<T> =
    distinctUntilChanged { old, new -> keySelector(old) == keySelector(new) }