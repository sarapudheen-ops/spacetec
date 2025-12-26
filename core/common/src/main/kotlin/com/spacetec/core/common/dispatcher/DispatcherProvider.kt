package com.spacetec.obd.core.common.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Interface for providing CoroutineDispatchers.
 * 
 * This abstraction allows for easy testing by substituting
 * test dispatchers during unit tests.
 */
interface DispatcherProvider {
    
    /**
     * Dispatcher optimized for IO-bound work like network or disk operations.
     */
    val io: CoroutineDispatcher
    
    /**
     * Dispatcher optimized for CPU-intensive work.
     */
    val default: CoroutineDispatcher
    
    /**
     * Main/UI thread dispatcher.
     */
    val main: CoroutineDispatcher
    
    /**
     * Main dispatcher that immediately executes when already on main thread.
     */
    val mainImmediate: CoroutineDispatcher
    
    /**
     * Unconfined dispatcher (for special cases).
     */
    val unconfined: CoroutineDispatcher
        get() = Dispatchers.Unconfined
}

/**
 * Default implementation using standard Kotlin dispatchers.
 */
data class SpaceTecDispatchers(
    override val io: CoroutineDispatcher = Dispatchers.IO,
    override val default: CoroutineDispatcher = Dispatchers.Default,
    override val main: CoroutineDispatcher = Dispatchers.Main,
    override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
) : DispatcherProvider

/**
 * Test implementation that uses a single dispatcher for all operations.
 * Useful for unit testing where you want deterministic execution.
 */
class TestDispatcherProvider(
    private val testDispatcher: CoroutineDispatcher
) : DispatcherProvider {
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}
