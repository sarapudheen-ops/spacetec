/*
 * FileLogger.kt
 *
 * File-based logging target with rotation, compression, and export capabilities
 * for SpaceTec scanner connection system diagnostics.
 *
 * Copyright 2024 SpaceTec Automotive Diagnostics
 * Licensed under the Apache License, Version 2.0
 */

package com.spacetec.core.logging

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * File logging configuration
 */
data class FileLoggerConfig(
    val baseFileName: String = "spacetec_scanner",
    val directory: File,
    val maxFileSizeBytes: Long = 10 * 1024 * 1024, // 10MB
    val maxFiles: Int = 10,
    val enableCompression: Boolean = true,
    val bufferSizeBytes: Int = 8192,
    val autoFlushIntervalMs: Long = 5000L,
    val includeStackTrace: Boolean = true,
    val dateFormat: String = "yyyy-MM-dd_HH-mm-ss",
    val fileExtension: String = "log"
) {
    val compressedExtension: String = if (enableCompression) "$fileExtension.gz" else fileExtension
    
    fun createFileName(timestamp: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat(dateFormat, Locale.US).format(Date(timestamp))
        return "${baseFileName}_$dateStr.$fileExtension"
    }
    
    fun createCompressedFileName(originalName: String): String {
        return if (enableCompression && !originalName.endsWith(".gz")) {
            "$originalName.gz"
        } else originalName
    }
}

/**
 * File-based log target with automatic rotation and compression
 */
class FileLogTarget(
    override val name: String = "FileTarget",
    override val minLevel: LogLevel = LogLevel.DEBUG,
    override val format: LogFormat = LogFormat.STANDARD,
    private val config: FileLoggerConfig
) : LogTarget {

    override var isEnabled: Boolean = true
        private set

    private val writeMutex = Mutex()
    private val bytesWritten = AtomicLong(0)
    private var currentFile: File? = null
    private var currentWriter: BufferedWriter? = null
    private var lastFlushTime = System.currentTimeMillis()

    init {
        // Ensure directory exists
        if (!config.directory.exists()) {
            config.directory.mkdirs()
        }
    }

    override suspend fun write(entry: LogEntry) {
        withContext(Dispatchers.IO) {
            if (!isEnabled) return@withContext

            writeMutex.withLock {
                try {
                    ensureCurrentFile()
                    val formattedEntry = formatEntry(entry)
                    
                    currentWriter?.let { writer ->
                        writer.write(formattedEntry)
                        writer.newLine()
                        
                        val entryBytes = formattedEntry.toByteArray().size + 1 // +1 for newline
                        bytesWritten.addAndGet(entryBytes.toLong())
                        
                        // Check if we need to rotate
                        if (bytesWritten.get() >= config.maxFileSizeBytes) {
                            rotateFile()
                        }
                        
                        // Auto-flush if interval exceeded
                        val now = System.currentTimeMillis()
                        if (now - lastFlushTime >= config.autoFlushIntervalMs) {
                            writer.flush()
                            lastFlushTime = now
                        }
                    }
                } catch (e: IOException) {
                    System.err.println("Failed to write log entry to file: ${e.message}")
                    // Disable target on persistent errors
                    if (e is FileNotFoundException || e is SecurityException) {
                        isEnabled = false
                    }
                    Unit
                }
            }
        }
    }

    override suspend fun flush() {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    currentWriter?.flush()
                    lastFlushTime = System.currentTimeMillis()
                } catch (e: IOException) {
                    System.err.println("Failed to flush log file: ${e.message}")
                }
            }
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    currentWriter?.close()
                    currentWriter = null
                    currentFile = null
                    isEnabled = false
                } catch (e: IOException) {
                    System.err.println("Failed to close log file: ${e.message}")
                }
            }
        }
    }

    private fun ensureCurrentFile() {
        if (currentFile == null || currentWriter == null) {
            val fileName = config.createFileName()
            currentFile = File(config.directory, fileName)
            currentWriter = BufferedWriter(
                FileWriter(currentFile, true), // append mode
                config.bufferSizeBytes
            )
            bytesWritten.set(currentFile?.length() ?: 0L)
        }
    }

    private suspend fun rotateFile() = withContext(Dispatchers.IO) {
        try {
            // Close current file
            currentWriter?.close()
            
            // Compress current file if enabled
            currentFile?.let { file ->
                if (config.enableCompression) {
                    compressFile(file)
                }
            }
            
            // Clean up old files
            cleanupOldFiles()
            
            // Reset for new file
            currentFile = null
            currentWriter = null
            bytesWritten.set(0)
            
        } catch (e: IOException) {
            System.err.println("Failed to rotate log file: ${e.message}")
        }
    }

    private suspend fun compressFile(file: File) {
        withContext(Dispatchers.IO) {
            val compressedFile = File(file.parent, config.createCompressedFileName(file.name))
            
            try {
                FileInputStream(file).use { fis ->
                    GZIPOutputStream(FileOutputStream(compressedFile)).use { gzos ->
                        fis.copyTo(gzos, config.bufferSizeBytes)
                    }
                }
                
                // Delete original file after successful compression
                if (compressedFile.exists() && compressedFile.length() > 0) {
                    file.delete()
                }
                Unit
            } catch (e: IOException) {
                System.err.println("Failed to compress log file ${file.name}: ${e.message}")
                // Keep original file if compression fails
                compressedFile.delete()
            }
        }
    }

    private fun cleanupOldFiles() {
        try {
            val logFiles = config.directory.listFiles { _, name ->
                name.startsWith(config.baseFileName) && 
                (name.endsWith(config.fileExtension) || name.endsWith(config.compressedExtension))
            }?.sortedByDescending { it.lastModified() } ?: return
            
            // Keep only the most recent files
            if (logFiles.size > config.maxFiles) {
                logFiles.drop(config.maxFiles).forEach { file ->
                    try {
                        file.delete()
                    } catch (e: SecurityException) {
                        System.err.println("Failed to delete old log file ${file.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to cleanup old log files: ${e.message}")
        }
    }

    private fun formatEntry(entry: LogEntry): String = buildString {
        append(entry.toFormattedString(format))
        
        // Add stack trace if enabled and throwable exists
        if (config.includeStackTrace && entry.throwable != null) {
            appendLine()
            append("Stack trace: ")
            val sw = StringWriter()
            entry.throwable.printStackTrace(PrintWriter(sw))
            append(sw.toString().prependIndent("  "))
        }
    }

    /**
     * Get information about current log files
     */
    fun getLogFileInfo(): LogFileInfo {
        val logFiles = config.directory.listFiles { _, name ->
            name.startsWith(config.baseFileName) && 
            (name.endsWith(config.fileExtension) || name.endsWith(config.compressedExtension))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
        
        val totalSize = logFiles.sumOf { it.length() }
        val currentFileSize = currentFile?.length() ?: 0L
        
        return LogFileInfo(
            files = logFiles.map { 
                LogFileDetails(
                    name = it.name,
                    path = it.absolutePath,
                    size = it.length(),
                    lastModified = it.lastModified(),
                    isCompressed = it.name.endsWith(".gz"),
                    isCurrent = it == currentFile
                )
            },
            totalSize = totalSize,
            currentFileSize = currentFileSize,
            maxFiles = config.maxFiles,
            maxFileSize = config.maxFileSizeBytes
        )
    }

    /**
     * Export log files to a specified directory
     */
    suspend fun exportLogs(
        exportDir: File,
        includeCompressed: Boolean = true,
        maxFiles: Int = Int.MAX_VALUE
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val logFiles = config.directory.listFiles { _, name ->
                name.startsWith(config.baseFileName) && 
                (name.endsWith(config.fileExtension) || 
                 (includeCompressed && name.endsWith(config.compressedExtension)))
            }?.sortedByDescending { it.lastModified() }?.take(maxFiles) ?: emptyList()
            
            val exportedFiles = mutableListOf<File>()
            var totalBytes = 0L
            
            for (file in logFiles) {
                val exportFile = File(exportDir, file.name)
                file.copyTo(exportFile, overwrite = true)
                exportedFiles.add(exportFile)
                totalBytes += file.length()
            }
            
            ExportResult.Success(
                exportedFiles = exportedFiles,
                totalBytes = totalBytes,
                exportDirectory = exportDir
            )
            
        } catch (e: IOException) {
            ExportResult.Error("Failed to export logs: ${e.message}", e)
        }
    }
}

/**
 * Log file information
 */
data class LogFileInfo(
    val files: List<LogFileDetails>,
    val totalSize: Long,
    val currentFileSize: Long,
    val maxFiles: Int,
    val maxFileSize: Long
) {
    val fileCount: Int = files.size
    val compressionRatio: Double = if (files.any { !it.isCompressed }) {
        val compressedSize = files.filter { it.isCompressed }.sumOf { it.size }
        val uncompressedSize = files.filter { !it.isCompressed }.sumOf { it.size }
        if (uncompressedSize > 0) compressedSize.toDouble() / uncompressedSize else 1.0
    } else 1.0
}

/**
 * Individual log file details
 */
data class LogFileDetails(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isCompressed: Boolean,
    val isCurrent: Boolean
) {
    val formattedSize: String = formatBytes(size)
    val formattedDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        .format(Date(lastModified))
}

/**
 * Export operation result
 */
sealed class ExportResult {
    data class Success(
        val exportedFiles: List<File>,
        val totalBytes: Long,
        val exportDirectory: File
    ) : ExportResult() {
        val fileCount: Int = exportedFiles.size
        val formattedSize: String = formatBytes(totalBytes)
    }
    
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ExportResult()
}

/**
 * Utility function to format bytes in human-readable format
 */
private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.2f %s".format(Locale.US, size, units[unitIndex])
}

/**
 * Console log target for development and debugging
 */
class ConsoleLogTarget(
    override val name: String = "ConsoleTarget",
    override val minLevel: LogLevel = LogLevel.DEBUG,
    override val format: LogFormat = LogFormat.STANDARD,
    private val useSystemOut: Boolean = true
) : LogTarget {

    override val isEnabled: Boolean = true

    override suspend fun write(entry: LogEntry) {
        val formattedEntry = entry.toFormattedString(format)
        
        if (useSystemOut || entry.level.priority < LogLevel.ERROR.priority) {
            println(formattedEntry)
        } else {
            System.err.println(formattedEntry)
        }
        
        // Print stack trace if present
        entry.throwable?.printStackTrace()
    }

    override suspend fun flush() {
        if (useSystemOut) {
            System.out.flush()
        } else {
            System.err.flush()
        }
    }

    override suspend fun close() {
        // Nothing to close for console output
    }
}