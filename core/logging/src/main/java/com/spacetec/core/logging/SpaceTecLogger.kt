package com.spacetec.core.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class SpaceTecLogger(
    override val tag: String,
    private val config: LoggerConfig = LoggerConfig(),
    private val sharedBuffer: ConcurrentLinkedQueue<LogEntry> = ConcurrentLinkedQueue()
) : Logger {

    override val minLevel: LogLevel = config.minLevel
    override val isEnabled: Boolean = true

    private val logBuffer = sharedBuffer
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    companion object {
        private const val MAX_BUFFER_SIZE = 1000
    }

    val entries: List<LogEntry> get() = logBuffer.toList()

    private fun log(
        level: LogLevel,
        message: String,
        throwable: Throwable? = null,
        context: Map<String, Any?> = emptyMap()
    ) {
        if (level.priority < minLevel.priority) return

        val finalMessage = if (config.maxMessageLength > 0 && message.length > config.maxMessageLength) {
            message.take(config.maxMessageLength) + "... [truncated]"
        } else {
            message
        }

        val mergedContext = config.defaultContext + context

        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = finalMessage,
            throwable = throwable,
            context = mergedContext,
            threadName = Thread.currentThread().name,
            correlationId = config.correlationId,
            operationId = config.operationId,
            sessionId = config.sessionId
        )

        // Add to buffer
        logBuffer.offer(entry)
        while (logBuffer.size > MAX_BUFFER_SIZE) {
            logBuffer.poll()
        }

        // Log to Android
        val fullTag = "SpaceTec:$tag"
        val androidMessage = if (mergedContext.isNotEmpty()) {
            "$finalMessage [${mergedContext.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
        } else finalMessage

        try {
            when (level) {
                LogLevel.VERBOSE -> Log.v(fullTag, androidMessage, throwable)
                LogLevel.DEBUG -> Log.d(fullTag, androidMessage, throwable)
                LogLevel.INFO -> Log.i(fullTag, androidMessage, throwable)
                LogLevel.WARN -> Log.w(fullTag, androidMessage, throwable)
                LogLevel.ERROR -> Log.e(fullTag, androidMessage, throwable)
                LogLevel.FATAL -> Log.e(fullTag, androidMessage, throwable)
            }
        } catch (_: Throwable) {
            // Ignore logging backend failures (e.g., android.util.Log stubs in local JVM unit tests)
        }
    }

    override fun verbose(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.VERBOSE, message, throwable, context)
    override fun debug(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.DEBUG, message, throwable, context)
    override fun info(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.INFO, message, throwable, context)
    override fun warn(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.WARN, message, throwable, context)
    override fun error(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.ERROR, message, throwable, context)
    override fun fatal(message: String, throwable: Throwable?, context: Map<String, Any?>) = log(LogLevel.FATAL, message, throwable, context)

    override fun verbose(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.VERBOSE.priority >= minLevel.priority) verbose(message(), throwable, context)
    }
    override fun debug(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.DEBUG.priority >= minLevel.priority) debug(message(), throwable, context)
    }
    override fun info(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.INFO.priority >= minLevel.priority) info(message(), throwable, context)
    }
    override fun warn(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.WARN.priority >= minLevel.priority) warn(message(), throwable, context)
    }
    override fun error(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.ERROR.priority >= minLevel.priority) error(message(), throwable, context)
    }
    override fun fatal(throwable: Throwable?, context: Map<String, Any?>, message: () -> String) {
        if (LogLevel.FATAL.priority >= minLevel.priority) fatal(message(), throwable, context)
    }

    override fun withContext(vararg pairs: Pair<String, Any?>): Logger {
        val newConfig = config.copy(defaultContext = config.defaultContext + pairs.toMap())
        return SpaceTecLogger(tag, newConfig, sharedBuffer)
    }

    override fun withCorrelationId(id: String): Logger {
        return SpaceTecLogger(tag, config.copy(correlationId = id), sharedBuffer)
    }

    override fun withOperationId(id: String): Logger {
        return SpaceTecLogger(tag, config.copy(operationId = id), sharedBuffer)
    }

    override fun withSessionId(id: String): Logger {
        return SpaceTecLogger(tag, config.copy(sessionId = id), sharedBuffer)
    }

    override fun isLoggable(level: LogLevel): Boolean = level.priority >= minLevel.priority

    fun getRecentLogs(count: Int = 100): List<LogEntry> = logBuffer.toList().takeLast(count)

    fun exportLogs(): String = logBuffer.joinToString("\n") { it.toFormattedString() }

    fun clearLogs() = logBuffer.clear()
}