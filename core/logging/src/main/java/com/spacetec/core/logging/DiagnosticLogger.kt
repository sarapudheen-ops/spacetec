/*
 * DiagnosticLogger.kt
 *
 * Specialized diagnostic logging for raw communication capture, protocol analysis,
 * and detailed scanner connection diagnostics for SpaceTec automotive system.
 *
 * Copyright 2024 SpaceTec Automotive Diagnostics
 * Licensed under the Apache License, Version 2.0
 */

package com.spacetec.core.logging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Extension functions for DiagnosticLogger to provide standard logging interface
 */

/**
 * Log debug message
 */
suspend fun DiagnosticLogger.debug(tag: String, message: String, context: Map<String, Any?> = emptyMap()) {
    logProtocolEvent("System", message, context + ("tag" to tag))
}

/**
 * Log info message
 */
suspend fun DiagnosticLogger.info(tag: String, message: String, context: Map<String, Any?> = emptyMap()) {
    logProtocolEvent("System", message, context + ("tag" to tag))
}

/**
 * Log warning message
 */
suspend fun DiagnosticLogger.warn(tag: String, message: String, context: Map<String, Any?> = emptyMap()) {
    logProtocolEvent("System", message, context + ("tag" to tag))
}

/**
 * Log error message
 */
suspend fun DiagnosticLogger.error(tag: String, message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap()) {
    logError(message, throwable, context + ("tag" to tag))
}

/**
 * Diagnostic logging modes
 */
enum class DiagnosticMode {
    DISABLED,           // No diagnostic logging
    BASIC,             // Basic operation logging
    DETAILED,          // Detailed operation and timing
    RAW_COMMUNICATION, // Raw data capture
    FULL_DEBUG         // Everything including internal state
}

/**
 * Communication direction for raw data capture
 */
enum class CommunicationDirection {
    OUTBOUND,  // Data sent to scanner
    INBOUND,   // Data received from scanner
    INTERNAL   // Internal system communication
}

/**
 * Raw communication entry for detailed protocol analysis
 */
data class RawCommunicationEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: CommunicationDirection,
    val connectionType: String, // "bluetooth", "wifi", "usb", "j2534"
    val connectionId: String,
    val data: ByteArray,
    val protocol: String? = null,
    val sessionId: String? = null,
    val operationId: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    val formattedTimestamp: String by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }
    
    val hexData: String by lazy {
        data.joinToString(" ") { "%02X".format(it) }
    }
    
    val asciiData: String by lazy {
        data.map { byte ->
            if (byte in 32..126) byte.toInt().toChar() else '.'
        }.joinToString("")
    }
    
    val dataSize: Int = data.size
    
    fun toFormattedString(includeHex: Boolean = true, includeAscii: Boolean = true): String = buildString {
        append("$formattedTimestamp ")
        append("${direction.name.padEnd(8)} ")
        append("${connectionType.uppercase().padEnd(9)} ")
        append("${connectionId.padEnd(12)} ")
        append("${dataSize.toString().padStart(4)} bytes")
        
        if (protocol != null) {
            append(" [$protocol]")
        }
        
        if (metadata.isNotEmpty()) {
            append(" {${metadata.entries.joinToString(", ") { "${it.key}=${it.value}" }}}")
        }
        
        if (includeHex && data.isNotEmpty()) {
            appendLine()
            append("  HEX: $hexData")
        }
        
        if (includeAscii && data.isNotEmpty()) {
            appendLine()
            append("  ASCII: $asciiData")
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as RawCommunicationEntry
        
        if (timestamp != other.timestamp) return false
        if (direction != other.direction) return false
        if (connectionType != other.connectionType) return false
        if (connectionId != other.connectionId) return false
        if (!data.contentEquals(other.data)) return false
        if (protocol != other.protocol) return false
        if (sessionId != other.sessionId) return false
        if (operationId != other.operationId) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + connectionType.hashCode()
        result = 31 * result + connectionId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (protocol?.hashCode() ?: 0)
        result = 31 * result + (sessionId?.hashCode() ?: 0)
        result = 31 * result + (operationId?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Diagnostic session information
 */
data class DiagnosticSession(
    val sessionId: String,
    val startTime: Long = System.currentTimeMillis(),
    val mode: DiagnosticMode,
    val connectionType: String,
    val connectionId: String,
    val vehicleInfo: Map<String, String> = emptyMap(),
    val scannerInfo: Map<String, String> = emptyMap()
) {
    var endTime: Long? = null
        private set
    
    val isActive: Boolean get() = endTime == null
    val duration: Long get() = (endTime ?: System.currentTimeMillis()) - startTime
    
    fun end() {
        endTime = System.currentTimeMillis()
    }
    
    fun toSummaryString(): String = buildString {
        appendLine("Diagnostic Session: $sessionId")
        appendLine("  Start: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(startTime))}")
        endTime?.let { 
            appendLine("  End: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it))}")
            appendLine("  Duration: ${duration}ms")
        }
        appendLine("  Mode: ${mode.name}")
        appendLine("  Connection: $connectionType ($connectionId)")
        if (vehicleInfo.isNotEmpty()) {
            appendLine("  Vehicle: ${vehicleInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        }
        if (scannerInfo.isNotEmpty()) {
            appendLine("  Scanner: ${scannerInfo.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        }
    }
}

/**
 * Diagnostic logger configuration
 */
data class DiagnosticLoggerConfig(
    val mode: DiagnosticMode = DiagnosticMode.BASIC,
    val captureRawData: Boolean = true,
    val maxRawDataSize: Int = 1024 * 1024, // 1MB per entry
    val maxBufferEntries: Int = 10000,
    val autoFlushIntervalMs: Long = 10000L,
    val enableTimingAnalysis: Boolean = true,
    val enableProtocolAnalysis: Boolean = true,
    val fileConfig: FileLoggerConfig? = null,
    val filters: List<DiagnosticFilter> = emptyList()
)

/**
 * Filter interface for diagnostic entries
 */
interface DiagnosticFilter {
    fun shouldCapture(entry: RawCommunicationEntry): Boolean
    fun shouldLog(entry: LogEntry): Boolean
}

/**
 * Main diagnostic logger implementation
 */
class DiagnosticLogger(
    private val config: DiagnosticLoggerConfig,
    private val baseLogger: Logger
) {
    private val mutex = Mutex()
    private val entryCounter = AtomicLong(0)
    
    private val rawCommunicationFlow = MutableSharedFlow<RawCommunicationEntry>(
        replay = 0,
        extraBufferCapacity = config.maxBufferEntries
    )
    
    private val diagnosticSessions = mutableMapOf<String, DiagnosticSession>()
    private var currentSession: DiagnosticSession? = null
    private var diagnosticFileTarget: FileLogTarget? = null
    
    val rawCommunication: Flow<RawCommunicationEntry> = rawCommunicationFlow.asSharedFlow()
    val isEnabled: Boolean get() = config.mode != DiagnosticMode.DISABLED
    val currentMode: DiagnosticMode get() = config.mode
    
    init {
        // Initialize file target if configured
        config.fileConfig?.let { fileConfig ->
            diagnosticFileTarget = FileLogTarget(
                name = "DiagnosticFileTarget",
                minLevel = LogLevel.DEBUG,
                format = LogFormat.STANDARD,
                config = fileConfig
            )
        }
    }
    
    /**
     * Start a new diagnostic session
     */
    suspend fun startSession(
        sessionId: String,
        connectionType: String,
        connectionId: String,
        vehicleInfo: Map<String, String> = emptyMap(),
        scannerInfo: Map<String, String> = emptyMap()
    ): DiagnosticSession = mutex.withLock {
        val session = DiagnosticSession(
            sessionId = sessionId,
            mode = config.mode,
            connectionType = connectionType,
            connectionId = connectionId,
            vehicleInfo = vehicleInfo,
            scannerInfo = scannerInfo
        )
        
        diagnosticSessions[sessionId] = session
        currentSession = session
        
        baseLogger.info("Started diagnostic session", context = mapOf(
            "sessionId" to sessionId,
            "mode" to config.mode.name,
            "connectionType" to connectionType,
            "connectionId" to connectionId
        ))
        
        // Log session start to file if configured
        diagnosticFileTarget?.write(LogEntry(
            level = LogLevel.INFO,
            tag = "DiagnosticSession",
            message = "Session started: ${session.toSummaryString()}"
        ))
        
        session
    }
    
    /**
     * End the current diagnostic session
     */
    suspend fun endSession(sessionId: String) = mutex.withLock {
        diagnosticSessions[sessionId]?.let { session ->
            session.end()
            
            baseLogger.info("Ended diagnostic session", context = mapOf(
                "sessionId" to sessionId,
                "duration" to session.duration
            ))
            
            // Log session end to file if configured
            diagnosticFileTarget?.write(LogEntry(
                level = LogLevel.INFO,
                tag = "DiagnosticSession",
                message = "Session ended: ${session.toSummaryString()}"
            ))
            
            if (currentSession?.sessionId == sessionId) {
                currentSession = null
            }
        }
    }
    
    /**
     * Log raw communication data
     */
    suspend fun logRawCommunication(
        direction: CommunicationDirection,
        connectionType: String,
        connectionId: String,
        data: ByteArray,
        protocol: String? = null,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        if (!isEnabled || !config.captureRawData) return
        if (data.size > config.maxRawDataSize) {
            baseLogger.warn("Raw data too large, truncating", context = mapOf(
                "originalSize" to data.size,
                "maxSize" to config.maxRawDataSize
            ))
        }
        
        val truncatedData = if (data.size > config.maxRawDataSize) {
            data.take(config.maxRawDataSize).toByteArray()
        } else data
        
        val entry = RawCommunicationEntry(
            direction = direction,
            connectionType = connectionType,
            connectionId = connectionId,
            data = truncatedData,
            protocol = protocol,
            sessionId = currentSession?.sessionId,
            operationId = metadata["operationId"] as? String,
            metadata = metadata
        )
        
        // Apply filters
        val shouldCapture = config.filters.isEmpty() || config.filters.all { it.shouldCapture(entry) }
        if (!shouldCapture) return
        
        // Emit to flow
        rawCommunicationFlow.tryEmit(entry)
        
        // Log to file if configured
        if (config.mode >= DiagnosticMode.RAW_COMMUNICATION) {
            diagnosticFileTarget?.write(LogEntry(
                level = LogLevel.DEBUG,
                tag = "RawComm",
                message = entry.toFormattedString(),
                context = mapOf(
                    "direction" to direction.name,
                    "connectionType" to connectionType,
                    "connectionId" to connectionId,
                    "dataSize" to data.size
                )
            ))
        }
        
        entryCounter.incrementAndGet()
    }
    
    /**
     * Log operation with timing information
     */
    suspend fun logOperation(
        operation: String,
        durationMs: Long,
        success: Boolean,
        details: Map<String, Any?> = emptyMap()
    ) {
        if (!isEnabled || config.mode < DiagnosticMode.DETAILED) return
        
        val level = if (success) LogLevel.INFO else LogLevel.WARN
        val context = mutableMapOf<String, Any?>(
            "operation" to operation,
            "duration" to durationMs,
            "success" to success,
            "sessionId" to currentSession?.sessionId
        ).apply { putAll(details) }
        
        val message = "Operation completed: $operation"
        if (success) {
            baseLogger.info(message, context = context)
        } else {
            baseLogger.warn(message, context = context)
        }
        
        // Detailed timing analysis
        if (config.enableTimingAnalysis && durationMs > 1000) {
            baseLogger.warn("Slow operation detected", context = mapOf(
                "operation" to operation,
                "duration" to durationMs,
                "threshold" to 1000
            ))
        }
    }
    
    /**
     * Log protocol-specific information
     */
    suspend fun logProtocolEvent(
        protocol: String,
        event: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        if (!isEnabled || !config.enableProtocolAnalysis) return
        
        val context = mutableMapOf<String, Any?>(
            "protocol" to protocol,
            "event" to event,
            "sessionId" to currentSession?.sessionId
        ).apply { putAll(data) }
        
        baseLogger.debug("Protocol event: $protocol - $event", context = context)
    }
    
    /**
     * Log error with full diagnostic context
     */
    suspend fun logError(
        error: String,
        throwable: Throwable? = null,
        context: Map<String, Any?> = emptyMap()
    ) {
        if (!isEnabled) return
        
        val diagnosticContext = mutableMapOf<String, Any?>(
            "sessionId" to currentSession?.sessionId,
            "entryCount" to entryCounter.get()
        ).apply { putAll(context) }
        
        baseLogger.error(error, throwable, diagnosticContext)
        
        // Capture system state for full debug mode
        if (config.mode == DiagnosticMode.FULL_DEBUG) {
            logSystemState()
        }
    }
    
    /**
     * Get diagnostic statistics
     */
    fun getStatistics(): DiagnosticStatistics {
        return DiagnosticStatistics(
            mode = config.mode,
            totalEntries = entryCounter.get(),
            activeSessions = diagnosticSessions.values.count { it.isActive },
            totalSessions = diagnosticSessions.size,
            currentSessionId = currentSession?.sessionId,
            bufferUtilization = rawCommunicationFlow.subscriptionCount.value.toDouble() / config.maxBufferEntries
        )
    }
    
    /**
     * Export diagnostic data
     */
    suspend fun exportDiagnosticData(
        exportDir: File,
        sessionId: String? = null,
        includeRawData: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val exportFiles = mutableListOf<File>()
            
            // Export session information
            val sessionFile = File(exportDir, "diagnostic_sessions.txt")
            sessionFile.writeText(buildString {
                if (sessionId != null) {
                    diagnosticSessions[sessionId]?.let { session ->
                        append(session.toSummaryString())
                    }
                } else {
                    diagnosticSessions.values.forEach { session ->
                        appendLine(session.toSummaryString())
                        appendLine("---")
                    }
                }
            })
            exportFiles.add(sessionFile)
            
            // Export log files if available
            diagnosticFileTarget?.exportLogs(exportDir, includeCompressed = true)?.let { result ->
                if (result is ExportResult.Success) {
                    exportFiles.addAll(result.exportedFiles)
                }
            }
            
            val totalBytes = exportFiles.sumOf { it.length() }
            
            ExportResult.Success(
                exportedFiles = exportFiles,
                totalBytes = totalBytes,
                exportDirectory = exportDir
            )
            
        } catch (e: IOException) {
            ExportResult.Error("Failed to export diagnostic data: ${e.message}", e)
        }
    }
    
    /**
     * Flush all diagnostic data
     */
    suspend fun flush() {
        diagnosticFileTarget?.flush()
    }
    
    /**
     * Close diagnostic logger and cleanup resources
     */
    suspend fun close() {
        mutex.withLock {
            // End all active sessions
            diagnosticSessions.values.filter { it.isActive }.forEach { session ->
                session.end()
            }
            
            diagnosticFileTarget?.close()
            currentSession = null
        }
    }
    
    private suspend fun logSystemState() {
        val runtime = Runtime.getRuntime()
        val context = mapOf(
            "freeMemory" to runtime.freeMemory(),
            "totalMemory" to runtime.totalMemory(),
            "maxMemory" to runtime.maxMemory(),
            "activeThreads" to Thread.activeCount(),
            "timestamp" to System.currentTimeMillis()
        )
        
        baseLogger.debug("System state captured", context = context)
    }
}

/**
 * Diagnostic statistics
 */
data class DiagnosticStatistics(
    val mode: DiagnosticMode,
    val totalEntries: Long,
    val activeSessions: Int,
    val totalSessions: Int,
    val currentSessionId: String?,
    val bufferUtilization: Double
)

/**
 * Extension function for Logger to add diagnostic capabilities
 */
fun Logger.withDiagnostics(config: DiagnosticLoggerConfig): DiagnosticLogger {
    return DiagnosticLogger(config, this)
}

/**
 * Utility functions for common diagnostic operations
 */
object DiagnosticUtils {
    fun createSessionId(): String = "diag_${System.currentTimeMillis()}_${(1000..9999).random()}"
    
    fun createOperationId(): String = "op_${System.currentTimeMillis()}_${(100..999).random()}"
    
    fun formatHexData(data: ByteArray, maxLength: Int = 64): String {
        val truncated = if (data.size > maxLength) data.take(maxLength).toByteArray() else data
        val hex = truncated.joinToString(" ") { "%02X".format(it) }
        return if (data.size > maxLength) "$hex... (${data.size} bytes total)" else hex
    }
    
    fun parseProtocolFromData(data: ByteArray): String? {
        if (data.isEmpty()) return null
        
        return when {
            // OBD-II responses typically start with 4x (where x is the service + 0x40)
            data.size >= 2 && data[0] in 0x41..0x4F -> "OBD-II"
            // UDS positive responses start with service + 0x40
            data.size >= 2 && data[0] in 0x50..0x7F -> "UDS"
            // UDS negative responses start with 0x7F
            data.size >= 3 && data[0] == 0x7F.toByte() -> "UDS-NRC"
            // CAN frames might have specific patterns
            data.size == 8 -> "CAN"
            else -> null
        }
    }
}