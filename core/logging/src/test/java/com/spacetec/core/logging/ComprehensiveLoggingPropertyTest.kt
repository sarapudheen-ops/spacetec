/*
 * Copyright (c) 2024 SpaceTec Automotive Diagnostics
 * All rights reserved.
 *
 * This file is part of the SpaceTec professional automotive diagnostic system.
 */

package com.spacetec.core.logging

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.cash.turbine.test
import java.io.File
import java.io.StringWriter
import java.io.PrintWriter

/**
 * **Feature: scanner-connection-system, Property 13: Comprehensive Operation Logging**
 * 
 * Property-based test for comprehensive logging system functionality.
 * 
 * **Validates: Requirements 8.1, 8.2, 8.3**
 * 
 * This test verifies that:
 * 1. All connection operations are logged with complete information including timestamps, commands, and responses
 * 2. Error logging includes complete error context including stack traces and system state
 * 3. Diagnostic mode captures raw communication data for analysis
 */
class ComprehensiveLoggingPropertyTest {
    
    @Test
    fun `Property 13 - All connection operations should be logged with complete information including timestamps and context`() = runBlocking {
        // Given: A logger with comprehensive configuration
        val config = LoggerConfig(
            minLevel = LogLevel.VERBOSE,
            enableStackTrace = true,
            enableThreadInfo = true,
            maxMessageLength = 1000
        )
        val logger = SpaceTecLogger("TestTag", config)
        
        // When: A log entry is created
        val startTime = System.currentTimeMillis()
        val context = mapOf("operation" to "test_operation", "connectionId" to "test_123")
        logger.info("Test message", null, context)
        
        // Then: The log entry should contain all required information
        assertTrue("Logger should have at least one entry", logger.entries.isNotEmpty())
        val logEntry = logger.entries.last()
        
        // Verify timestamp is recent and reasonable
        assertTrue("Timestamp should be recent", logEntry.timestamp >= startTime)
        assertTrue("Timestamp should be within 1 second", logEntry.timestamp <= startTime + 1000)
        
        // Verify all basic fields are present
        assertEquals(LogLevel.INFO, logEntry.level)
        assertEquals("TestTag", logEntry.tag)
        assertEquals("Test message", logEntry.message)
        assertEquals(context, logEntry.context)
        
        // Verify thread information is captured
        assertNotNull("Thread name should not be null", logEntry.threadName)
        assertTrue("Thread name should not be empty", logEntry.threadName.isNotEmpty())
        
        // Verify formatted timestamp is valid
        assertNotNull("Formatted timestamp should not be null", logEntry.formattedTimestamp)
        assertTrue("Formatted timestamp should not be empty", logEntry.formattedTimestamp.isNotEmpty())
        assertTrue("Formatted timestamp should contain date separators", logEntry.formattedTimestamp.contains("-"))
        assertTrue("Formatted timestamp should contain time separators", logEntry.formattedTimestamp.contains(":"))
        
        // Verify formatted string contains all information
        val formattedString = logEntry.toFormattedString()
        assertTrue("Should contain timestamp", formattedString.contains(logEntry.formattedTimestamp))
        assertTrue("Should contain level tag", formattedString.contains(LogLevel.INFO.tag))
        assertTrue("Should contain tag", formattedString.contains("TestTag"))
        assertTrue("Should contain message", formattedString.contains("Test message"))
        
        // Verify context is included in formatted string
        assertTrue("Should contain operation context", formattedString.contains("operation=test_operation"))
        assertTrue("Should contain connectionId context", formattedString.contains("connectionId=test_123"))
    }
    
    @Test
    fun `Property 13 - Error logging should include complete error context including stack traces and system state`() = runTest {
        // Given: A logger configured for comprehensive error logging
        val config = LoggerConfig(
            minLevel = LogLevel.DEBUG,
            enableStackTrace = true,
            enableThreadInfo = true
        )
        val logger = SpaceTecLogger("ErrorTest", config)
        
        // When: An error is logged
        val errorMessage = "Test error message"
        val throwable = RuntimeException("Test runtime exception")
        val errorContext = mapOf(
            "operation" to "test_operation",
            "connectionId" to "test_connection_123",
            "duration" to 1500L,
            "retryCount" to 2
        )
        
        logger.error(errorMessage, throwable, errorContext)
        
        // Then: The log entry should contain complete error information
        assertTrue("Logger should have at least one entry", logger.entries.isNotEmpty())
        val logEntry = logger.entries.last()
        
        // Verify error level and basic information
        assertEquals(LogLevel.ERROR, logEntry.level)
        assertEquals("ErrorTest", logEntry.tag)
        assertEquals(errorMessage, logEntry.message)
        assertEquals(throwable, logEntry.throwable)
        
        // Verify error context is preserved
        assertEquals(errorContext, logEntry.context)
        assertEquals("test_operation", logEntry.context["operation"])
        assertEquals("test_connection_123", logEntry.context["connectionId"])
        assertEquals(1500L, logEntry.context["duration"])
        assertEquals(2, logEntry.context["retryCount"])
        
        // Verify formatted string includes error details
        val formattedString = logEntry.toFormattedString()
        assertTrue("Should contain ERROR level", formattedString.contains("ERROR"))
        assertTrue("Should contain error message", formattedString.contains(errorMessage))
        
        // Verify context information is included
        assertTrue("Should contain operation", formattedString.contains("operation=test_operation"))
        assertTrue("Should contain connectionId", formattedString.contains("connectionId=test_connection_123"))
        assertTrue("Should contain duration", formattedString.contains("duration=1500"))
        assertTrue("Should contain retryCount", formattedString.contains("retryCount=2"))
        
        // Verify throwable is properly handled
        assertNotNull("Throwable should be captured", logEntry.throwable)
        assertEquals("Exception message should match", "Test runtime exception", logEntry.throwable?.message)
    }
    
    @Test
    fun `Property 13 - Diagnostic mode should capture raw communication data with complete metadata`() = runTest {
        // Given: A diagnostic logger in raw communication mode
        val baseLogger = SpaceTecLogger("DiagnosticTest")
        val diagnosticConfig = DiagnosticLoggerConfig(
            mode = DiagnosticMode.RAW_COMMUNICATION,
            captureRawData = true,
            maxRawDataSize = 1024,
            enableProtocolAnalysis = true
        )
        val diagnosticLogger = DiagnosticLogger(diagnosticConfig, baseLogger)
        
        // When & Then: Test the raw communication flow using turbine
        diagnosticLogger.rawCommunication.test {
            val direction = CommunicationDirection.OUTBOUND
            val connectionType = "bluetooth"
            val connectionId = "bt_test_123"
            val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
            val protocol = "OBD-II"
            val metadata = mapOf(
                "operationId" to "op_test_123",
                "ecuAddress" to "0x7E0",
                "responseTime" to 150L
            )
            
            diagnosticLogger.logRawCommunication(
                direction = direction,
                connectionType = connectionType,
                connectionId = connectionId,
                data = data,
                protocol = protocol,
                metadata = metadata
            )
            
            // Then: The raw communication entry should contain complete information
            val rawEntry = awaitItem()
        
            // Verify all basic fields
            assertEquals(direction, rawEntry.direction)
            assertEquals(connectionType, rawEntry.connectionType)
            assertEquals(connectionId, rawEntry.connectionId)
            assertArrayEquals(data, rawEntry.data)
            assertEquals(protocol, rawEntry.protocol)
            assertEquals(metadata, rawEntry.metadata)
            
            // Verify derived fields
            assertEquals(data.size, rawEntry.dataSize)
            assertEquals("01 02 03 04 05", rawEntry.hexData)
            
            // Verify ASCII representation
            val expectedAscii = data.map { byte ->
                if (byte in 32..126) byte.toInt().toChar() else '.'
            }.joinToString("")
            assertEquals(expectedAscii, rawEntry.asciiData)
            
            // Verify timestamp is reasonable
            val now = System.currentTimeMillis()
            assertTrue("Timestamp should be recent", rawEntry.timestamp >= now - 1000)
            assertTrue("Timestamp should not be in future", rawEntry.timestamp <= now)
            
            // Verify formatted string contains all information
            val formattedString = rawEntry.toFormattedString()
            assertTrue("Should contain direction", formattedString.contains(direction.name))
            assertTrue("Should contain connection type", formattedString.contains(connectionType.uppercase()))
            assertTrue("Should contain connection ID", formattedString.contains(connectionId))
            assertTrue("Should contain data size", formattedString.contains("${data.size} bytes"))
            
            if (protocol != null) {
                assertTrue("Should contain protocol", formattedString.contains("[$protocol]"))
            }
            
            // Verify metadata is included
            assertTrue("Should contain operationId", formattedString.contains("operationId=op_test_123"))
            assertTrue("Should contain ecuAddress", formattedString.contains("ecuAddress=0x7E0"))
            assertTrue("Should contain responseTime", formattedString.contains("responseTime=150"))
            
            // Verify hex data is included
            assertTrue("Should contain hex data", formattedString.contains("HEX: 01 02 03 04 05"))
            assertTrue("Should contain ASCII data", formattedString.contains("ASCII: $expectedAscii"))
            
            cancelAndConsumeRemainingEvents()
        }
    }
    
    @Test
    fun `Property 13 - Log entries should be filterable and configurable based on level`() = runBlocking {
        // Given: A logger with specific minimum level
        val config = LoggerConfig(minLevel = LogLevel.WARN)
        val logger = SpaceTecLogger("FilterTest", config)
        
        // When: Log entries are attempted at various levels
        logger.debug("Debug message") // Should be filtered out
        logger.warn("Warning message") // Should be logged
        logger.error("Error message") // Should be logged
        
        // Then: Only entries at or above the minimum level should be logged
        assertFalse("Debug level should not be loggable", logger.isLoggable(LogLevel.DEBUG))
        assertTrue("Warn level should be loggable", logger.isLoggable(LogLevel.WARN))
        assertTrue("Error level should be loggable", logger.isLoggable(LogLevel.ERROR))
    }
    
    @Test
    fun `Property 13 - Context information should be preserved and accessible in all log formats`() = runBlocking {
        // Given: A logger with context information
        val context = mapOf(
            "stringValue" to "test_string",
            "intValue" to 42,
            "longValue" to 1234L,
            "booleanValue" to true
        )
        val correlationId = "corr_123"
        val operationId = "op_456"
        val sessionId = "session_789"
        
        // Create the base logger first to access entries
        val baseLogger = SpaceTecLogger("ContextTest")
        
        // Apply context using the fluent API but log to the base logger
        val contextualLogger = baseLogger
            .withContext(*context.toList().toTypedArray())
            .withCorrelationId(correlationId)
            .withOperationId(operationId)
            .withSessionId(sessionId)
        
        // When: A log entry is created using the contextual logger
        contextualLogger.info("Test message with context")
        
        // Then: All context information should be preserved
        assertTrue("Base logger should have at least one entry", baseLogger.entries.isNotEmpty())
        val logEntry = baseLogger.entries.last()
        
        // Verify context is preserved
        context.forEach { (key, value) ->
            assertEquals("Context value should match for key $key", value, logEntry.context[key])
        }
        
        // Verify correlation, operation, and session IDs
        assertEquals(correlationId, logEntry.correlationId)
        assertEquals(operationId, logEntry.operationId)
        assertEquals(sessionId, logEntry.sessionId)
        
        // Verify all formats include context information
        val standardFormat = logEntry.toFormattedString(LogFormat.STANDARD)
        val jsonFormat = logEntry.toFormattedString(LogFormat.JSON)
        val compactFormat = logEntry.toFormattedString(LogFormat.COMPACT)
        
        // Standard format should include context in brackets
        context.forEach { (key, value) ->
            assertTrue("Standard format should contain $key=$value", standardFormat.contains("$key=$value"))
        }
        assertTrue("Standard format should contain correlation ID", standardFormat.contains("(corr:$correlationId)"))
        assertTrue("Standard format should contain operation ID", standardFormat.contains("(op:$operationId)"))
        
        // JSON format should include all fields
        assertTrue("JSON format should contain correlationId", jsonFormat.contains("\"correlationId\":\"$correlationId\""))
        assertTrue("JSON format should contain operationId", jsonFormat.contains("\"operationId\":\"$operationId\""))
        assertTrue("JSON format should contain sessionId", jsonFormat.contains("\"sessionId\":\"$sessionId\""))
        
        // Compact format should include correlation ID
        assertTrue("Compact format should contain correlation ID", compactFormat.contains("[$correlationId]"))
    }
    
    @Test
    fun `Property 13 - Large messages should be truncated appropriately while preserving essential information`() = runBlocking {
        // Given: A logger with message length limits
        val maxLength = 100
        val config = LoggerConfig(maxMessageLength = maxLength)
        val logger = SpaceTecLogger("TruncationTest", config)
        
        // When: A message longer than the limit is logged
        val longMessage = "A".repeat(200) // 200 characters, longer than limit
        logger.info(longMessage)
        
        // Then: The message should be truncated appropriately
        assertTrue("Logger should have at least one entry", logger.entries.isNotEmpty())
        val logEntry = logger.entries.last()
        
        // Message should be truncated to max length + truncation indicator
        assertTrue("Message should be truncated", logEntry.message.length <= maxLength + 20) // Allow for truncation text
        assertTrue("Should contain truncation indicator", logEntry.message.contains("... [truncated]"))
        
        // Should contain the beginning of the original message
        val expectedPrefix = longMessage.take(maxLength)
        assertTrue("Should contain beginning of original message", logEntry.message.startsWith(expectedPrefix.take(50)))
    }
}