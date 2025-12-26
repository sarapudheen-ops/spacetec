/*
 * Logger.kt
 *
 * Comprehensive logging framework for SpaceTec scanner connection system.
 * Provides structured logging with timestamps, context, configurable levels,
 * and multiple output targets for automotive diagnostic operations.
 *
 * Copyright 2024 SpaceTec Automotive Diagnostics
 * Licensed under the Apache License, Version 2.0
 */

package com.spacetec.core.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Log levels in order of severity
 */
enum class LogLevel(val priority: Int, val tag: String) {
    VERBOSE(0, "V"),
    DEBUG(1, "D"),
    INFO(2, "I"),
    WARN(3, "W"),
    ERROR(4, "E"),
    FATAL(5, "F");

    companion object {
        fun fromString(level: String): LogLevel = when (level.uppercase()) {
            "V", "VERBOSE" -> VERBOSE
            "D", "DEBUG" -> DEBUG
            "I", "INFO" -> INFO
            "W", "WARN" -> WARN
            "E", "ERROR" -> ERROR
            "F", "FATAL" -> FATAL
            else -> INFO
        }
    }
}

/**
 * Structured log entry with timestamp and context
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val context: Map<String, Any?> = emptyMap(),
    val threadName: String = Thread.currentThread().name,
    val correlationId: String? = null,
    val operationId: String? = null,
    val sessionId: String? = null
) {
    val formattedTimestamp: String by lazy {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }

    fun toFormattedString(format: LogFormat = LogFormat.STANDARD): String = when (format) {
        LogFormat.STANDARD -> buildString {
            append("$formattedTimestamp ${level.name}(${level.tag})/$tag: $message")
            if (context.isNotEmpty()) {
                append(" [")
                append(context.entries.joinToString(", ") { "${it.key}=${it.value}" })
                append("]")
            }
            correlationId?.let { append(" (corr:$it)") }
            operationId?.let { append(" (op:$it)") }
        }
        LogFormat.JSON -> buildString {
            append("{")
            append("\"timestamp\":\"$formattedTimestamp\",")
            append("\"level\":\"${level.name}\",")
            append("\"tag\":\"$tag\",")
            append("\"message\":\"${message.replace("\"", "\\\"")}\",")
            append("\"thread\":\"$threadName\"")
            correlationId?.let { append(",\"correlationId\":\"$it\"") }
            operationId?.let { append(",\"operationId\":\"$it\"") }
            sessionId?.let { append(",\"sessionId\":\"$it\"") }
            if (context.isNotEmpty()) {
                append(",\"context\":{")
                append(context.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" })
                append("}")
            }
            throwable?.let { 
                append(",\"exception\":\"${it::class.simpleName}\",")
                append("\"exceptionMessage\":\"${it.message?.replace("\"", "\\\"") ?: ""}\"")
            }
            append("}")
        }
        LogFormat.COMPACT -> buildString {
            append("${level.tag} $tag: $message")
            correlationId?.let { append(" [$it]") }
        }
    }

    fun withContext(vararg pairs: Pair<String, Any?>): LogEntry = 
        copy(context = context + pairs.toMap())

    fun withCorrelationId(id: String): LogEntry = copy(correlationId = id)
    fun withOperationId(id: String): LogEntry = copy(operationId = id)
    fun withSessionId(id: String): LogEntry = copy(sessionId = id)
}

/**
 * Log output formats
 */
enum class LogFormat {
    STANDARD,   // Human-readable format
    JSON,       // JSON format for structured logging
    COMPACT     // Minimal format for performance
}

/**
 * Log output target interface
 */
interface LogTarget {
    val name: String
    val minLevel: LogLevel
    val format: LogFormat
    val isEnabled: Boolean

    suspend fun write(entry: LogEntry)
    suspend fun flush()
    suspend fun close()
}

/**
 * Log filter interface for conditional logging
 */
interface LogFilter {
    fun shouldLog(entry: LogEntry): Boolean
}

/**
 * Main logger interface
 */
interface Logger {
    val tag: String
    val minLevel: LogLevel
    val isEnabled: Boolean

    // Basic logging methods
    fun verbose(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())
    fun debug(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())
    fun info(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())
    fun warn(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())
    fun error(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())
    fun fatal(message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap())

    // Lazy logging methods
    fun verbose(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)
    fun debug(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)
    fun info(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)
    fun warn(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)
    fun error(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)
    fun fatal(throwable: Throwable? = null, context: Map<String, Any?> = emptyMap(), message: () -> String)

    // Contextual logging
    fun withContext(vararg pairs: Pair<String, Any?>): Logger
    fun withCorrelationId(id: String): Logger
    fun withOperationId(id: String): Logger
    fun withSessionId(id: String): Logger

    // Level checking
    fun isLoggable(level: LogLevel): Boolean
}

/**
 * Logger configuration
 */
data class LoggerConfig(
    val minLevel: LogLevel = LogLevel.INFO,
    val targets: List<LogTarget> = emptyList(),
    val filters: List<LogFilter> = emptyList(),
    val bufferSize: Int = 1000,
    val flushIntervalMs: Long = 5000L,
    val enableStackTrace: Boolean = false,
    val enableThreadInfo: Boolean = true,
    val maxMessageLength: Int = 10000,
    val defaultContext: Map<String, Any?> = emptyMap(),
    val correlationId: String? = null,
    val operationId: String? = null,
    val sessionId: String? = null
) {
    companion object {
        val DEFAULT = LoggerConfig()
        val DEBUG = LoggerConfig(
            minLevel = LogLevel.DEBUG,
            enableStackTrace = true,
            enableThreadInfo = true
        )
        val PRODUCTION = LoggerConfig(
            minLevel = LogLevel.INFO,
            enableStackTrace = false,
            enableThreadInfo = false,
            maxMessageLength = 5000
        )
    }
}

