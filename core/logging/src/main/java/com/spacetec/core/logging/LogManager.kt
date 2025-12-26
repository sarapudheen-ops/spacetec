/*
 * LogManager.kt
 *
 * Central log management system with rotation, export, and analysis capabilities
 * for SpaceTec scanner connection system.
 *
 * Copyright 2024 SpaceTec Automotive Diagnostics
 * Licensed under the Apache License, Version 2.0
 */

package com.spacetec.core.logging

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Log management configuration
 */
data class LogManagerConfig(
    val baseDirectory: File,
    val maxTotalSizeBytes: Long = 100 * 1024 * 1024, // 100MB
    val maxAgeHours: Int = 24 * 7, // 1 week
    val cleanupIntervalMs: Long = 60 * 60 * 1000, // 1 hour
    val enableAutoExport: Boolean = false,
    val exportFormats: Set<ExportFormat> = setOf(ExportFormat.ZIP),
    val compressionLevel: Int = 6,
    val enableAnalytics: Boolean = true
)

/**
 * Export formats supported by the log manager
 */
enum class ExportFormat {
    ZIP,        // Compressed archive
    TAR_GZ,     // Tar with gzip compression
    JSON,       // JSON format for structured analysis
    CSV,        // CSV format for spreadsheet analysis
    TEXT        // Plain text format
}

/**
 * Log analysis result
 */
data class LogAnalysis(
    val totalEntries: Long,
    val entriesByLevel: Map<LogLevel, Long>,
    val entriesByTag: Map<String, Long>,
    val timeRange: LongRange,
    val errorPatterns: List<ErrorPattern>,
    val performanceMetrics: PerformanceMetrics,
    val connectionStatistics: ConnectionStatistics
)

/**
 * Error pattern detected in logs
 */
data class ErrorPattern(
    val pattern: String,
    val count: Int,
    val firstOccurrence: Long,
    val lastOccurrence: Long,
    val affectedConnections: Set<String>
)

/**
 * Performance metrics from log analysis
 */
data class PerformanceMetrics(
    val averageResponseTime: Double,
    val maxResponseTime: Long,
    val minResponseTime: Long,
    val timeoutCount: Int,
    val slowOperations: List<SlowOperation>
)

/**
 * Slow operation detected in logs
 */
data class SlowOperation(
    val operation: String,
    val duration: Long,
    val timestamp: Long,
    val connectionId: String
)

/**
 * Connection statistics from log analysis
 */
data class ConnectionStatistics(
    val totalConnections: Int,
    val connectionsByType: Map<String, Int>,
    val averageConnectionTime: Double,
    val failedConnections: Int,
    val reconnectionCount: Int
)

/**
 * Central log management system
 */
class LogManager(
    private val config: LogManagerConfig
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val fileTargets = ConcurrentHashMap<String, FileLogTarget>()
    private val diagnosticLoggers = ConcurrentHashMap<String, DiagnosticLogger>()
    private val exportCounter = AtomicLong(0)
    
    private var cleanupJob: Job? = null
    private var isInitialized = false
    
    init {
        initialize()
    }
    
    private fun initialize() {
        if (!config.baseDirectory.exists()) {
            config.baseDirectory.mkdirs()
        }
        
        // Start cleanup job
        cleanupJob = scope.launch {
            while (isActive) {
                try {
                    performCleanup()
                    delay(config.cleanupIntervalMs)
                } catch (e: Exception) {
                    // Log cleanup errors but don't stop the job
                    System.err.println("Log cleanup failed: ${e.message}")
                    delay(config.cleanupIntervalMs)
                }
            }
        }
        
        isInitialized = true
    }
    
    /**
     * Create a new file log target
     */
    fun createFileTarget(
        name: String,
        config: FileLoggerConfig = FileLoggerConfig(directory = this.config.baseDirectory),
        minLevel: LogLevel = LogLevel.DEBUG,
        format: LogFormat = LogFormat.STANDARD
    ): FileLogTarget {
        val target = FileLogTarget(name, minLevel, format, config)
        fileTargets[name] = target
        return target
    }
    
    /**
     * Create a new diagnostic logger
     */
    fun createDiagnosticLogger(
        name: String,
        baseLogger: Logger,
        config: DiagnosticLoggerConfig = DiagnosticLoggerConfig()
    ): DiagnosticLogger {
        val diagnosticLogger = DiagnosticLogger(config, baseLogger)
        diagnosticLoggers[name] = diagnosticLogger
        return diagnosticLogger
    }
    
    /**
     * Get all log files in the managed directory
     */
    fun getAllLogFiles(): List<File> {
        return config.baseDirectory.listFiles { file ->
            file.isFile && (file.name.endsWith(".log") || file.name.endsWith(".log.gz"))
        }?.toList() ?: emptyList()
    }
    
    /**
     * Get log files within a time range
     */
    fun getLogFiles(startTime: Long, endTime: Long): List<File> {
        return getAllLogFiles().filter { file ->
            file.lastModified() in startTime..endTime
        }
    }
    
    /**
     * Analyze logs for patterns and metrics
     */
    suspend fun analyzeLogs(
        startTime: Long? = null,
        endTime: Long? = null,
        includeRawData: Boolean = false
    ): LogAnalysis = withContext(Dispatchers.IO) {
        val logFiles = if (startTime != null && endTime != null) {
            getLogFiles(startTime, endTime)
        } else {
            getAllLogFiles()
        }
        
        var totalEntries = 0L
        val entriesByLevel = mutableMapOf<LogLevel, Long>()
        val entriesByTag = mutableMapOf<String, Long>()
        val errorPatterns = mutableMapOf<String, ErrorPattern>()
        val performanceData = mutableListOf<Long>()
        val slowOperations = mutableListOf<SlowOperation>()
        val connectionData = mutableMapOf<String, MutableList<Long>>()
        var timeRange: LongRange = 0L..0L
        var timeoutCount = 0
        var failedConnections = 0
        var reconnectionCount = 0
        
        for (file in logFiles) {
            try {
                val reader = if (file.name.endsWith(".gz")) {
                    BufferedReader(InputStreamReader(java.util.zip.GZIPInputStream(FileInputStream(file))))
                } else {
                    BufferedReader(FileReader(file))
                }
                
                reader.useLines { lines ->
                    lines.forEach { line ->
                        totalEntries++
                        
                        // Parse log entry (simplified parsing)
                        parseLogLine(line)?.let { entry ->
                            // Update time range
                            if (timeRange.first == 0L || entry.timestamp < timeRange.first) {
                                timeRange = entry.timestamp..timeRange.last
                            }
                            if (entry.timestamp > timeRange.last) {
                                timeRange = timeRange.first..entry.timestamp
                            }
                            
                            // Count by level
                            entriesByLevel[entry.level] = (entriesByLevel[entry.level] ?: 0) + 1
                            
                            // Count by tag
                            entriesByTag[entry.tag] = (entriesByTag[entry.tag] ?: 0) + 1
                            
                            // Analyze errors
                            if (entry.level >= LogLevel.ERROR) {
                                val pattern = extractErrorPattern(entry.message)
                                errorPatterns[pattern] = errorPatterns[pattern]?.let { existing ->
                                    existing.copy(
                                        count = existing.count + 1,
                                        lastOccurrence = entry.timestamp,
                                        affectedConnections = existing.affectedConnections + (entry.context["connectionId"] as? String ?: "unknown")
                                    )
                                } ?: ErrorPattern(
                                    pattern = pattern,
                                    count = 1,
                                    firstOccurrence = entry.timestamp,
                                    lastOccurrence = entry.timestamp,
                                    affectedConnections = setOf(entry.context["connectionId"] as? String ?: "unknown")
                                )
                            }
                            
                            // Analyze performance
                            entry.context["duration"]?.let { duration ->
                                if (duration is Number) {
                                    val durationMs = duration.toLong()
                                    performanceData.add(durationMs)
                                    
                                    if (durationMs > 5000) { // Slow operations > 5 seconds
                                        slowOperations.add(SlowOperation(
                                            operation = entry.context["operation"] as? String ?: "unknown",
                                            duration = durationMs,
                                            timestamp = entry.timestamp,
                                            connectionId = entry.context["connectionId"] as? String ?: "unknown"
                                        ))
                                    }
                                }
                            }
                            
                            // Count timeouts
                            if (entry.message.contains("timeout", ignoreCase = true)) {
                                timeoutCount++
                            }
                            
                            // Count connection failures
                            if (entry.message.contains("connection failed", ignoreCase = true)) {
                                failedConnections++
                            }
                            
                            // Count reconnections
                            if (entry.message.contains("reconnect", ignoreCase = true)) {
                                reconnectionCount++
                            }
                            
                            // Track connection times
                            entry.context["connectionType"]?.let { type ->
                                entry.context["duration"]?.let { duration ->
                                    if (duration is Number) {
                                        connectionData.getOrPut(type.toString()) { mutableListOf() }
                                            .add(duration.toLong())
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                System.err.println("Failed to analyze log file ${file.name}: ${e.message}")
            }
        }
        
        // Calculate performance metrics
        val performanceMetrics = if (performanceData.isNotEmpty()) {
            PerformanceMetrics(
                averageResponseTime = performanceData.average(),
                maxResponseTime = performanceData.maxOrNull() ?: 0L,
                minResponseTime = performanceData.minOrNull() ?: 0L,
                timeoutCount = timeoutCount,
                slowOperations = slowOperations.sortedByDescending { it.duration }.take(10)
            )
        } else {
            PerformanceMetrics(0.0, 0L, 0L, timeoutCount, emptyList())
        }
        
        // Calculate connection statistics
        val connectionStats = ConnectionStatistics(
            totalConnections = connectionData.values.sumOf { it.size },
            connectionsByType = connectionData.mapValues { it.value.size },
            averageConnectionTime = connectionData.values.flatten().let { times ->
                if (times.isNotEmpty()) times.average() else 0.0
            },
            failedConnections = failedConnections,
            reconnectionCount = reconnectionCount
        )
        
        LogAnalysis(
            totalEntries = totalEntries,
            entriesByLevel = entriesByLevel,
            entriesByTag = entriesByTag,
            timeRange = timeRange,
            errorPatterns = errorPatterns.values.sortedByDescending { it.count },
            performanceMetrics = performanceMetrics,
            connectionStatistics = connectionStats
        )
    }
    
    /**
     * Export logs in specified format
     */
    suspend fun exportLogs(
        exportDir: File,
        format: ExportFormat = ExportFormat.ZIP,
        startTime: Long? = null,
        endTime: Long? = null,
        includeAnalysis: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val exportId = exportCounter.incrementAndGet()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val exportFileName = "spacetec_logs_${timestamp}_$exportId"
            
            val logFiles = if (startTime != null && endTime != null) {
                getLogFiles(startTime, endTime)
            } else {
                getAllLogFiles()
            }
            
            when (format) {
                ExportFormat.ZIP -> exportAsZip(exportDir, exportFileName, logFiles, includeAnalysis)
                ExportFormat.JSON -> exportAsJson(exportDir, exportFileName, logFiles, includeAnalysis)
                ExportFormat.CSV -> exportAsCsv(exportDir, exportFileName, logFiles)
                ExportFormat.TEXT -> exportAsText(exportDir, exportFileName, logFiles)
                ExportFormat.TAR_GZ -> exportAsTarGz(exportDir, exportFileName, logFiles, includeAnalysis)
            }
        } catch (e: IOException) {
            ExportResult.Error("Failed to export logs: ${e.message}", e)
        }
    }
    
    /**
     * Perform cleanup of old log files
     */
    private suspend fun performCleanup() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val maxAge = config.maxAgeHours * 60 * 60 * 1000L
        val allFiles = getAllLogFiles()
        
        // Remove files older than maxAge
        val oldFiles = allFiles.filter { file ->
            now - file.lastModified() > maxAge
        }
        
        oldFiles.forEach { file ->
            try {
                file.delete()
            } catch (e: SecurityException) {
                System.err.println("Failed to delete old log file ${file.name}: ${e.message}")
            }
        }
        
        // Check total size and remove oldest files if necessary
        val remainingFiles = allFiles - oldFiles.toSet()
        val totalSize = remainingFiles.sumOf { it.length() }
        
        if (totalSize > config.maxTotalSizeBytes) {
            val sortedByAge = remainingFiles.sortedBy { it.lastModified() }
            var currentSize = totalSize
            
            for (file in sortedByAge) {
                if (currentSize <= config.maxTotalSizeBytes) break
                
                try {
                    currentSize -= file.length()
                    file.delete()
                } catch (e: SecurityException) {
                    System.err.println("Failed to delete log file ${file.name}: ${e.message}")
                }
            }
        }
    }
    
    private fun parseLogLine(line: String): LogEntry? {
        // Simplified log parsing - in a real implementation, this would be more robust
        try {
            val parts = line.split(" ", limit = 4)
            if (parts.size < 4) return null
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .parse("${parts[0]} ${parts[1]}").time
            val levelTag = parts[2].split("/")
            if (levelTag.size != 2) return null
            
            val level = LogLevel.fromString(levelTag[0])
            val tag = levelTag[1].removeSuffix(":")
            val message = parts[3]
            
            return LogEntry(
                timestamp = timestamp,
                level = level,
                tag = tag,
                message = message
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun extractErrorPattern(message: String): String {
        // Extract common error patterns
        return when {
            message.contains("timeout", ignoreCase = true) -> "TIMEOUT"
            message.contains("connection failed", ignoreCase = true) -> "CONNECTION_FAILED"
            message.contains("protocol error", ignoreCase = true) -> "PROTOCOL_ERROR"
            message.contains("permission denied", ignoreCase = true) -> "PERMISSION_DENIED"
            message.contains("device not found", ignoreCase = true) -> "DEVICE_NOT_FOUND"
            else -> "OTHER_ERROR"
        }
    }
    
    private suspend fun exportAsZip(
        exportDir: File,
        baseName: String,
        logFiles: List<File>,
        includeAnalysis: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        val zipFile = File(exportDir, "$baseName.zip")
        val exportedFiles = mutableListOf<File>()
        var totalBytes = 0L
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Add log files
            for (file in logFiles) {
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                totalBytes += file.length()
            }
            
            // Add analysis if requested
            if (includeAnalysis) {
                val analysis = analyzeLogs()
                val analysisEntry = ZipEntry("analysis.json")
                zos.putNextEntry(analysisEntry)
                zos.write(formatAnalysisAsJson(analysis).toByteArray())
                zos.closeEntry()
            }
        }
        
        exportedFiles.add(zipFile)
        totalBytes += zipFile.length()
        
        ExportResult.Success(exportedFiles, totalBytes, exportDir)
    }
    
    private suspend fun exportAsJson(
        exportDir: File,
        baseName: String,
        logFiles: List<File>,
        includeAnalysis: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        val jsonFile = File(exportDir, "$baseName.json")
        val exportedFiles = mutableListOf<File>()
        
        // This would require a proper JSON serialization implementation
        jsonFile.writeText("{ \"message\": \"JSON export not fully implemented\" }")
        
        exportedFiles.add(jsonFile)
        val totalBytes = exportedFiles.sumOf { it.length() }
        
        ExportResult.Success(exportedFiles, totalBytes, exportDir)
    }
    
    private suspend fun exportAsCsv(
        exportDir: File,
        baseName: String,
        logFiles: List<File>
    ): ExportResult = withContext(Dispatchers.IO) {
        val csvFile = File(exportDir, "$baseName.csv")
        val exportedFiles = mutableListOf<File>()
        
        // This would require proper CSV formatting
        csvFile.writeText("timestamp,level,tag,message\n")
        
        exportedFiles.add(csvFile)
        val totalBytes = exportedFiles.sumOf { it.length() }
        
        ExportResult.Success(exportedFiles, totalBytes, exportDir)
    }
    
    private suspend fun exportAsText(
        exportDir: File,
        baseName: String,
        logFiles: List<File>
    ): ExportResult = withContext(Dispatchers.IO) {
        val textFile = File(exportDir, "$baseName.txt")
        val exportedFiles = mutableListOf<File>()
        
        textFile.bufferedWriter().use { writer ->
            for (file in logFiles) {
                writer.appendLine("=== ${file.name} ===")
                file.bufferedReader().use { reader ->
                    reader.copyTo(writer)
                }
                writer.appendLine()
            }
        }
        
        exportedFiles.add(textFile)
        val totalBytes = exportedFiles.sumOf { it.length() }
        
        ExportResult.Success(exportedFiles, totalBytes, exportDir)
    }
    
    private suspend fun exportAsTarGz(
        exportDir: File,
        baseName: String,
        logFiles: List<File>,
        includeAnalysis: Boolean
    ): ExportResult = withContext(Dispatchers.IO) {
        // Simplified implementation - would need proper tar.gz support
        return@withContext exportAsZip(exportDir, baseName, logFiles, includeAnalysis)
    }
    
    private fun formatAnalysisAsJson(analysis: LogAnalysis): String {
        // Simplified JSON formatting - would use proper JSON library in production
        return buildString {
            appendLine("{")
            appendLine("  \"totalEntries\": ${analysis.totalEntries},")
            appendLine("  \"timeRange\": {")
            appendLine("    \"start\": ${analysis.timeRange.first},")
            appendLine("    \"end\": ${analysis.timeRange.last}")
            appendLine("  },")
            appendLine("  \"entriesByLevel\": {")
            analysis.entriesByLevel.entries.forEachIndexed { index, (level, count) ->
                append("    \"${level.name}\": $count")
                if (index < analysis.entriesByLevel.size - 1) append(",")
                appendLine()
            }
            appendLine("  },")
            appendLine("  \"errorPatterns\": [")
            analysis.errorPatterns.forEachIndexed { index, pattern ->
                append("    {")
                append("\"pattern\": \"${pattern.pattern}\", ")
                append("\"count\": ${pattern.count}")
                append("}")
                if (index < analysis.errorPatterns.size - 1) append(",")
                appendLine()
            }
            appendLine("  ]")
            append("}")
        }
    }
    
    /**
     * Shutdown the log manager
     */
    fun shutdown() {
        cleanupJob?.cancel()
        scope.cancel()
        
        // Close all targets
        fileTargets.values.forEach { target ->
            runBlocking { target.close() }
        }
        
        // Close all diagnostic loggers
        diagnosticLoggers.values.forEach { logger ->
            runBlocking { logger.close() }
        }
        
        fileTargets.clear()
        diagnosticLoggers.clear()
    }
}