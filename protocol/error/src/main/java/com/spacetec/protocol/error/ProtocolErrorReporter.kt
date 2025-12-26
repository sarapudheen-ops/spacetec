package com.spacetec.protocol.error

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.Date

/**
 * Error reporting and monitoring system for diagnostic protocols
 */
class ProtocolErrorReporter {
    
    /**
     * Represents an error event with metadata
     */
    data class ErrorEvent(
        val timestamp: Long = System.currentTimeMillis(),
        val error: ProtocolErrorHandler.ProtocolError,
        val context: String,
        val protocolType: String,
        val sessionType: String? = null,
        val serviceId: Int? = null,
        val severity: ErrorSeverity
    ) {
        enum class ErrorSeverity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    /**
     * Error statistics for monitoring
     */
    data class ErrorStatistics(
        val totalErrors: Int = 0,
        val errorTypes: Map<String, Int> = emptyMap(),
        val lastErrorTime: Long? = null,
        val recentErrors: List<ErrorEvent> = emptyList()
    )
    
    private val _errorEvents = MutableSharedFlow<ErrorEvent>(extraBufferCapacity = 100)
    val errorEvents = _errorEvents.asSharedFlow()
    
    private val errorCounts = ConcurrentHashMap<String, Int>()
    private val recentErrors = mutableListOf<ErrorEvent>()
    private val maxRecentErrors = 50
    
    /**
     * Reports an error with context information
     */
    suspend fun reportError(
        error: ProtocolErrorHandler.ProtocolError,
        context: String,
        protocolType: String,
        sessionType: String? = null,
        serviceId: Int? = null
    ) {
        val severity = determineErrorSeverity(error)
        val errorEvent = ErrorEvent(
            error = error,
            context = context,
            protocolType = protocolType,
            sessionType = sessionType,
            serviceId = serviceId,
            severity = severity
        )
        
        // Add to recent errors
        synchronized(recentErrors) {
            recentErrors.add(0, errorEvent) // Add to beginning
            if (recentErrors.size > maxRecentErrors) {
                recentErrors.removeAt(recentErrors.size - 1) // Remove oldest
            }
        }
        
        // Update error counts
        val errorKey = getErrorKey(error)
        errorCounts[errorKey] = errorCounts.getOrDefault(errorKey, 0) + 1
        
        // Emit the error event
        _errorEvents.emit(errorEvent)
    }
    
    /**
     * Determines the severity of an error
     */
    private fun determineErrorSeverity(error: ProtocolErrorHandler.ProtocolError): ErrorEvent.ErrorSeverity {
        return when (error) {
            is ProtocolErrorHandler.ProtocolError.CommunicationError -> ErrorEvent.ErrorSeverity.HIGH
            is ProtocolErrorHandler.ProtocolError.TimeoutError -> ErrorEvent.ErrorSeverity.MEDIUM
            is ProtocolErrorHandler.ProtocolError.NegativeResponseError -> {
                when (error.nrc) {
                    0x10, 0x11, 0x12 -> ErrorEvent.ErrorSeverity.LOW // General errors
                    0x22, 0x24, 0x31, 0x33 -> ErrorEvent.ErrorSeverity.HIGH // Conditions/security
                    0x72, 0x78 -> ErrorEvent.ErrorSeverity.CRITICAL // Programming errors
                    else -> ErrorEvent.ErrorSeverity.MEDIUM
                }
            }
            is ProtocolErrorHandler.ProtocolError.ValidationError -> ErrorEvent.ErrorSeverity.LOW
            is ProtocolErrorHandler.ProtocolError.SessionError -> ErrorEvent.ErrorSeverity.HIGH
            is ProtocolErrorHandler.ProtocolError.SecurityError -> ErrorEvent.ErrorSeverity.CRITICAL
            is ProtocolErrorHandler.ProtocolError.NotImplementedError -> ErrorEvent.ErrorSeverity.LOW
        }
    }
    
    /**
     * Gets a unique key for the error type
     */
    private fun getErrorKey(error: ProtocolErrorHandler.ProtocolError): String {
        return when (error) {
            is ProtocolErrorHandler.ProtocolError.NegativeResponseError -> {
                "NRC_${error.nrc}_SERVICE_${error.serviceId}"
            }
            else -> {
                error::class.simpleName ?: "UNKNOWN"
            }
        }
    }
    
    /**
     * Gets current error statistics
     */
    fun getErrorStatistics(): ErrorStatistics {
        val counts = errorCounts.toMap()
        val total = counts.values.sum()
        val lastError = synchronized(recentErrors) {
            recentErrors.firstOrNull()
        }
        
        return ErrorStatistics(
            totalErrors = total,
            errorTypes = counts,
            lastErrorTime = lastError?.timestamp,
            recentErrors = synchronized(recentErrors) { recentErrors.toList() }
        )
    }
    
    /**
     * Checks if error rate is excessive (potential issue)
     */
    fun isExcessiveErrorRate(windowMs: Long = 60000, maxErrors: Int = 5): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs
        
        val recentCount = synchronized(recentErrors) {
            recentErrors.count { it.timestamp > windowStart }
        }
        
        return recentCount >= maxErrors
    }
    
    /**
     * Clears all error statistics
     */
    fun clearStatistics() {
        errorCounts.clear()
        synchronized(recentErrors) {
            recentErrors.clear()
        }
    }
    
    /**
     * Gets error trends over time
     */
    fun getErrorTrends(hours: Int = 24): Map<String, Int> {
        val now = System.currentTimeMillis()
        val startTime = now - (hours * 60 * 60 * 1000L)
        
        return synchronized(recentErrors) {
            recentErrors
                .filter { it.timestamp >= startTime }
                .groupBy { it.error::class.simpleName ?: "UNKNOWN" }
                .mapValues { it.value.size }
        }
    }
}

/**
 * Extension function to easily report errors
 */
suspend fun ProtocolErrorReporter.reportProtocolError(
    error: Throwable,
    context: String,
    protocolType: String,
    sessionType: String? = null,
    serviceId: Int? = null
) {
    val protocolError = error.toProtocolError()
    this.reportError(protocolError, context, protocolType, sessionType, serviceId)
}