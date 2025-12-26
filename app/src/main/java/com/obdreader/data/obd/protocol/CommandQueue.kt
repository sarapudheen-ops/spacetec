package com.obdreader.data.obd.protocol

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages command queue for OBD communication.
 * Ensures commands are sent sequentially and responses are properly matched.
 */
class CommandQueue(
    private val maxQueueSize: Int = 10,
    private val defaultTimeout: Long = 2000
) {
    private val queue = ConcurrentLinkedQueue<QueuedCommand>()
    private val mutex = Mutex()
    private val isProcessing = AtomicBoolean(false)
    
    data class QueuedCommand(
        val command: String,
        val timeout: Long,
        val response: CompletableDeferred<String>,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Queue a command for execution.
     * @throws CommandQueueFullException if queue is at capacity
     */
    suspend fun enqueue(
        command: String,
        timeout: Long = defaultTimeout
    ): CompletableDeferred<String> {
        if (queue.size >= maxQueueSize) {
            throw CommandQueueFullException("Queue is full (max: $maxQueueSize)")
        }
        
        val deferred = CompletableDeferred<String>()
        val queuedCommand = QueuedCommand(command, timeout, deferred)
        
        mutex.withLock {
            queue.offer(queuedCommand)
        }
        
        return deferred
    }
    
    /**
     * Get the next command to process.
     */
    suspend fun dequeue(): QueuedCommand? = mutex.withLock {
        queue.poll()
    }
    
    /**
     * Get the current pending command.
     */
    fun peek(): QueuedCommand? = queue.peek()
    
    /**
     * Check if queue is empty.
     */
    fun isEmpty(): Boolean = queue.isEmpty()
    
    /**
     * Get current queue size.
     */
    fun size(): Int = queue.size
    
    /**
     * Clear all pending commands with error.
     */
    suspend fun clearWithError(error: Throwable) = mutex.withLock {
        while (queue.isNotEmpty()) {
            queue.poll()?.response?.completeExceptionally(error)
        }
    }
    
    /**
     * Clear all pending commands.
     */
    suspend fun clear() = mutex.withLock {
        queue.clear()
    }
    
    /**
     * Mark processing state.
     */
    fun setProcessing(processing: Boolean) {
        isProcessing.set(processing)
    }
    
    /**
     * Check if currently processing a command.
     */
    fun isProcessing(): Boolean = isProcessing.get()
}

class CommandQueueFullException(message: String) : Exception(message)
