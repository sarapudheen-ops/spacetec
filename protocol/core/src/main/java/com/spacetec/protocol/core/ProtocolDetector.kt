/**
 * File: ProtocolDetector.kt
 * 
 * Protocol detection system for the SpaceTec automotive diagnostic application.
 * This class implements the OBD-II protocol detection algorithm as specified in
 * SAE J1978 and ISO 15031-5, automatically identifying the communication protocol
 * supported by the connected vehicle.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. ProtocolDetector class with detection algorithms
 * 3. DetectionConfig data class
 * 4. DetectionProgress sealed class hierarchy
 * 5. DetectionResult and ProtocolTestResult data classes
 * 6. Supporting enums and helper classes
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.protocol.core

import com.spacetec.protocol.core.timing.TimingManager
import com.spacetec.protocol.core.message.DiagnosticMessage
import com.spacetec.core.common.NRCConstants
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.constants.OBDConstants
import com.spacetec.transport.contract.ProtocolFactory
import com.spacetec.transport.contract.ScannerConnection
import com.spacetec.transport.contract.ProtocolType
import com.spacetec.core.logging.SpaceTecLogger
import com.spacetec.core.domain.protocol.ProtocolState
import com.spacetec.core.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the communication protocol supported by the connected vehicle.
 * 
 * This class implements the OBD-II protocol detection algorithm, testing
 * protocols in an optimized order to minimize detection time while ensuring
 * accurate protocol identification.
 * 
 * ## Detection Order
 * 
 * The detection order is optimized based on protocol prevalence:
 * 1. **ISO 15765-4 CAN 11-bit 500k** - Most modern vehicles (2008+)
 * 2. **ISO 15765-4 CAN 29-bit 500k** - Heavy duty vehicles
 * 3. **ISO 15765-4 CAN 11-bit 250k** - Some European vehicles
 * 4. **ISO 15765-4 CAN 29-bit 250k** - Heavy duty variants
 * 5. **ISO 14230-4 KWP Fast Init** - European vehicles (1996-2008)
 * 6. **ISO 14230-4 KWP 5-baud Init** - Older European vehicles
 * 7. **ISO 9141-2** - Asian and European (1996-2004)
 * 8. **SAE J1850 VPW** - GM vehicles (1996-2008)
 * 9. **SAE J1850 PWM** - Ford vehicles (1996-2008)
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * val detector = ProtocolDetector(protocolFactory, timingManager, logger)
 * 
 * // Simple detection
 * val protocol = detector.detectProtocol(connection)
 * 
 * // Detection with progress updates
 * detector.detectProtocolWithProgress(connection).collect { progress ->
 *     when (progress) {
 *         is DetectionProgress.Testing -> updateUI(progress.protocol)
 *         is DetectionProgress.Detected -> onSuccess(progress.protocol)
 *         is DetectionProgress.Failed -> onError(progress.error)
 *     }
 * }
 * ```
 * 
 * ## Thread Safety
 * 
 * This class is thread-safe. Only one detection operation can run at a time.
 * Calling [detectProtocol] while another detection is in progress will wait
 * for the previous operation to complete.
 * 
 * @property protocolFactory Factory for creating protocol instances
 * @property timingManager Manager for protocol timing requirements
 * @property logger Logger for diagnostic output
 * 
 * @see ProtocolType
 * @see DetectionConfig
 * @see DetectionProgress
 */
@Singleton
class ProtocolDetector @Inject constructor(
    private val protocolFactory: ProtocolFactory,
    private val timingManager: TimingManager,
    private val logger: SpaceTecLogger
) {
    
    // ==================== Private Properties ====================
    
    /**
     * Coroutine scope for detection operations.
     */
    private val detectorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Mutex to ensure only one detection runs at a time.
     */
    private val detectionMutex = Mutex()
    
    /**
     * Current detection job for cancellation support.
     */
    private var currentDetectionJob: Job? = null
    
    /**
     * Flag indicating if detection should be cancelled.
     */
    private val isCancelled = AtomicBoolean(false)
    
    /**
     * Current detection state.
     */
    private val _detectionState = MutableStateFlow<DetectionState>(DetectionState.Idle)
    
    /**
     * Observable detection state.
     */
    val detectionState: StateFlow<DetectionState>
        get() = _detectionState.asStateFlow()
    
    /**
     * Whether detection is currently in progress.
     */
    val isDetecting: Boolean
        get() = _detectionState.value is DetectionState.Detecting
    
    // ==================== Public Detection Methods ====================
    
    /**
     * Detects the vehicle's communication protocol.
     * 
     * This method tests protocols in an optimized order until one responds
     * successfully. The detection can be customized using [DetectionConfig].
     * 
     * @param connection Active scanner connection
     * @param config Detection configuration (defaults to [DetectionConfig.DEFAULT])
     * @return The detected [ProtocolType]
     * 
     * @throws ProtocolException If no protocol is detected
     * @throws CommunicationException If communication fails
     * @throws CancellationException If detection is cancelled
     * 
     * @see detectProtocolWithProgress
     * @see testProtocol
     */
    suspend fun detectProtocol(
        connection: ScannerConnection,
        config: DetectionConfig = DetectionConfig.DEFAULT
    ): ProtocolType = detectionMutex.withLock {
        withContext(Dispatchers.IO) {
            logger.info("Starting protocol detection")
            
            isCancelled.set(false)
            _detectionState.value = DetectionState.Detecting(0f, null)
            
            val startTime = System.currentTimeMillis()
            val protocolOrder = getDetectionOrder(config = config)
            val testedProtocols = mutableMapOf<ProtocolType, ProtocolTestResult>()
            
            try {
                withTimeout(config.totalTimeoutMs) {
                    for ((index, protocol) in protocolOrder.withIndex()) {
                        if (isCancelled.get()) {
                            throw CancellationException("Detection cancelled")
                        }
                        
                        if (protocol in config.skipProtocols) {
                            logger.debug("Skipping protocol: $protocol")
                            continue
                        }
                        
                        val progress = (index + 1).toFloat() / protocolOrder.size
                        _detectionState.value = DetectionState.Detecting(progress, protocol)
                        
                        logger.info("Testing protocol ${index + 1}/${protocolOrder.size}: $protocol")
                        
                        val testResult = testProtocolInternal(connection, protocol, config)
                        testedProtocols[protocol] = testResult
                        
                        if (testResult.success) {
                            val detectionTime = System.currentTimeMillis() - startTime
                            logger.info("Protocol detected: $protocol in ${detectionTime}ms")
                            
                            _detectionState.value = DetectionState.Detected(protocol, detectionTime)
                            return@withTimeout protocol
                        }
                    }
                    
                    // No protocol detected
                    val error = ProtocolException("No compatible protocol found")
                    _detectionState.value = DetectionState.Failed(error, testedProtocols.keys.toList())
                    throw error
                }
            } catch (e: CancellationException) {
                _detectionState.value = DetectionState.Cancelled
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                val error = ProtocolException("Detection timeout after ${config.totalTimeoutMs}ms")
                _detectionState.value = DetectionState.Failed(error, testedProtocols.keys.toList())
                throw error
            } catch (e: Exception) {
                if (e !is ProtocolException) {
                    _detectionState.value = DetectionState.Failed(e, testedProtocols.keys.toList())
                }
                throw e
            }
        }
    }
    
    /**
     * Detects protocol with progress updates via Flow.
     * 
     * This method provides real-time updates during the detection process,
     * allowing UI to display progress and intermediate results.
     * 
     * @param connection Active scanner connection
     * @param config Detection configuration
     * @return Flow emitting [DetectionProgress] updates
     * 
     * @see detectProtocol
     */
    fun detectProtocolWithProgress(
        connection: ScannerConnection,
        config: DetectionConfig = DetectionConfig.DEFAULT
    ): Flow<DetectionProgress> = flow {
        emit(DetectionProgress.Started)
        
        isCancelled.set(false)
        val startTime = System.currentTimeMillis()
        val protocolOrder = getDetectionOrder(config = config)
        val testedProtocols = mutableMapOf<ProtocolType, ProtocolTestResult>()
        var detectedProtocol: ProtocolType? = null
        
        try {
            withTimeout(config.totalTimeoutMs) {
                for ((index, protocol) in protocolOrder.withIndex()) {
                    if (isCancelled.get()) {
                        emit(DetectionProgress.Cancelled)
                        return@withTimeout
                    }
                    
                    if (protocol in config.skipProtocols) {
                        continue
                    }
                    
                    val progress = (index + 1).toFloat() / protocolOrder.size
                    emit(DetectionProgress.Testing(
                        protocol = protocol,
                        protocolIndex = index + 1,
                        totalProtocols = protocolOrder.size,
                        progress = progress
                    ))
                    
                    val testResult = testProtocolInternal(connection, protocol, config)
                    testedProtocols[protocol] = testResult
                    
                    emit(DetectionProgress.TestedProtocol(
                        protocol = protocol,
                        success = testResult.success,
                        responseTime = testResult.responseTimeMs
                    ))
                    
                    if (testResult.success) {
                        detectedProtocol = protocol
                        
                        if (config.stopOnFirstMatch) {
                            break
                        }
                    }
                }
            }
            
            val detectionTime = System.currentTimeMillis() - startTime
            
            if (detectedProtocol != null) {
                emit(DetectionProgress.Detected(
                    protocol = detectedProtocol,
                    detectionTime = detectionTime
                ))
            } else {
                emit(DetectionProgress.Failed(
                    error = ProtocolError.NoProtocolFound("No compatible protocol detected"),
                    testedProtocols = testedProtocols.keys.toList()
                ))
            }
            
        } catch (e: CancellationException) {
            emit(DetectionProgress.Cancelled)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            emit(DetectionProgress.Failed(
                error = ProtocolError.Timeout("Detection timeout"),
                testedProtocols = testedProtocols.keys.toList()
            ))
        } catch (e: Exception) {
            emit(DetectionProgress.Failed(
                error = ProtocolError.CommunicationError(e.message ?: "Unknown error"),
                testedProtocols = testedProtocols.keys.toList()
            ))
        }
    }
    
    /**
     * Tests if a specific protocol is supported by the vehicle.
     * 
     * @param connection Active scanner connection
     * @param protocol Protocol to test
     * @return true if protocol is supported
     */
    suspend fun testProtocol(
        connection: ScannerConnection,
        protocol: ProtocolType
    ): Boolean {
        logger.debug("Testing specific protocol: $protocol")
        val result = testProtocolInternal(connection, protocol, DetectionConfig.DEFAULT)
        return result.success
    }
    
    /**
     * Gets the detection order based on optional vehicle hints.
     * 
     * Vehicle hints can optimize detection by prioritizing likely protocols:
     * - European vehicles: KWP2000 before J1850
     * - American vehicles: J1850 before KWP2000
     * - Modern vehicles (2008+): CAN protocols only
     * - Heavy duty vehicles: 29-bit CAN first
     * 
     * @param vehicleYear Vehicle model year (optional)
     * @param vehicleMake Vehicle manufacturer (optional)
     * @param config Detection configuration
     * @return Ordered list of protocols to test
     */
    fun getDetectionOrder(
        vehicleYear: Int? = null,
        vehicleMake: String? = null,
        config: DetectionConfig = DetectionConfig.DEFAULT
    ): List<ProtocolType> {
        // Start with preferred protocol if specified
        val order = mutableListOf<ProtocolType>()
        
        config.preferredProtocol?.let {
            if (it !in config.skipProtocols) {
                order.add(it)
            }
        }
        
        // Determine base order based on vehicle hints
        val baseOrder = when {
            // Modern vehicles (2008+) - CAN only
            vehicleYear != null && vehicleYear >= 2008 -> listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K
            )
            
            // Heavy duty vehicle makes
            vehicleMake?.uppercase() in HEAVY_DUTY_MAKES -> listOf(
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2
            )
            
            // GM vehicles - check VPW first after CAN
            vehicleMake?.uppercase() in GM_MAKES -> listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_PWM
            )
            
            // Ford vehicles - check PWM first after CAN
            vehicleMake?.uppercase() in FORD_MAKES -> listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.SAE_J1850_PWM,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_VPW
            )
            
            // European vehicles - KWP before J1850
            vehicleMake?.uppercase() in EUROPEAN_MAKES -> listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM
            )
            
            // Asian vehicles - ISO 9141 is common
            vehicleMake?.uppercase() in ASIAN_MAKES -> listOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_9141_2,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM
            )
            
            // Default order - optimized for most common protocols
            else -> DEFAULT_DETECTION_ORDER
        }
        
        // Add base order protocols not already in the list
        for (protocol in baseOrder) {
            if (protocol !in order && protocol !in config.skipProtocols) {
                order.add(protocol)
            }
        }
        
        return order
    }
    
    /**
     * Cancels ongoing detection.
     * 
     * This method can be called from any thread. The detection operation
     * will be cancelled at the next safe point and throw [CancellationException].
     */
    fun cancelDetection() {
        logger.info("Detection cancellation requested")
        isCancelled.set(true)
        currentDetectionJob?.cancel()
    }
    
    // ==================== Internal Detection Methods ====================
    
    /**
     * Internal method to test a specific protocol.
     */
    private suspend fun testProtocolInternal(
        connection: ScannerConnection,
        protocol: ProtocolType,
        config: DetectionConfig
    ): ProtocolTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = when (protocol.category) {
                ProtocolCategory.CAN -> testCANProtocol(connection, protocol, config)
                ProtocolCategory.KWP2000 -> testKWP2000Protocol(connection, protocol, config)
                ProtocolCategory.ISO9141 -> testISO9141Protocol(connection, config)
                ProtocolCategory.J1850 -> testJ1850Protocol(connection, protocol, config)
                ProtocolCategory.J1939 -> testJ1939Protocol(connection, config)
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            
            ProtocolTestResult(
                protocol = protocol,
                success = success,
                responseTimeMs = responseTime
            )
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            logger.debug("Protocol $protocol test failed: ${e.message}")
            
            ProtocolTestResult(
                protocol = protocol,
                success = false,
                responseTimeMs = responseTime,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Tests CAN-based protocols (ISO 15765-4).
     */
    private suspend fun testCANProtocol(
        connection: ScannerConnection,
        protocol: ProtocolType,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing CAN protocol: $protocol")
        
        val is29Bit = protocol in listOf(
            ProtocolType.ISO_15765_4_CAN_29BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_250K
        )
        
        val baudRate = when (protocol) {
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_500K -> 500000
            ProtocolType.ISO_15765_4_CAN_11BIT_250K,
            ProtocolType.ISO_15765_4_CAN_29BIT_250K -> 250000
            else -> 500000
        }
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure CAN parameters
            configureCANProtocol(connection, is29Bit, baudRate)
            
            // Send OBD-II Mode 01 PID 00 request (supported PIDs)
            val request = if (is29Bit) {
                buildCAN29BitRequest(OBD_BROADCAST_29BIT, byteArrayOf(0x02, 0x01, 0x00))
            } else {
                buildCAN11BitRequest(OBD_BROADCAST_11BIT, byteArrayOf(0x02, 0x01, 0x00))
            }
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs)
            
            if (response != null && isValidOBDResponse(response)) {
                logger.debug("CAN protocol $protocol: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures CAN protocol parameters on the adapter.
     */
    private suspend fun configureCANProtocol(
        connection: ScannerConnection,
        is29Bit: Boolean,
        baudRate: Int
    ) {
        // Send AT commands to configure ELM327-compatible adapters
        val commands = mutableListOf<String>()
        
        // Reset adapter
        commands.add("ATZ")
        
        // Disable echo
        commands.add("ATE0")
        
        // Disable line feeds
        commands.add("ATL0")
        
        // Disable spaces
        commands.add("ATS0")
        
        // Set protocol
        val protocolCode = when {
            is29Bit && baudRate == 500000 -> "6" // ISO 15765-4 CAN 29/500
            is29Bit && baudRate == 250000 -> "8" // ISO 15765-4 CAN 29/250
            !is29Bit && baudRate == 500000 -> "6" // ISO 15765-4 CAN 11/500
            !is29Bit && baudRate == 250000 -> "8" // ISO 15765-4 CAN 11/250
            else -> "0" // Auto
        }
        commands.add("ATSP$protocolCode")
        
        // Set headers on
        commands.add("ATH1")
        
        // Set CAN ID filter if needed
        if (is29Bit) {
            commands.add("ATCF18DAF100")
            commands.add("ATCM1FFFFFFF")
        }
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    /**
     * Builds an 11-bit CAN request frame.
     */
    private fun buildCAN11BitRequest(canId: Int, data: ByteArray): ByteArray {
        // For ELM327: just send the data, header is set via ATSH
        return data
    }
    
    /**
     * Builds a 29-bit CAN request frame.
     */
    private fun buildCAN29BitRequest(canId: Int, data: ByteArray): ByteArray {
        // For ELM327: just send the data, header is set via ATSH
        return data
    }
    
    /**
     * Tests KWP2000-based protocols (ISO 14230-4).
     */
    private suspend fun testKWP2000Protocol(
        connection: ScannerConnection,
        protocol: ProtocolType,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing KWP2000 protocol: $protocol")
        
        return when (protocol) {
            ProtocolType.ISO_14230_4_KWP_FAST -> testKWP2000FastInit(connection, config)
            else -> false
        }
    }
    
    /**
     * Tests KWP2000 with fast initialization.
     */
    private suspend fun testKWP2000FastInit(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing KWP2000 Fast Init")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for KWP Fast Init
            configureKWP2000(connection, fastInit = true)
            
            // Fast init uses a 25ms low pulse followed by start communication
            // For ELM327: ATSP5 sets KWP Fast Init
            
            // Send start communication request
            val request = byteArrayOf(
                0xC1.toByte(), // Format byte (physical addressing, 1 byte)
                0x33.toByte(), // Target address (ECU)
                0xF1.toByte(), // Source address (tester)
                0x81.toByte(), // Start Communication Service
                0x66.toByte()  // Checksum
            )
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs)
            
            if (response != null && isValidKWPResponse(response)) {
                logger.debug("KWP2000 Fast Init: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Tests KWP2000 with 5-baud slow initialization.
     */
    private suspend fun testKWP2000SlowInit(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing KWP2000 5-Baud Slow Init")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for KWP 5-baud Init
            configureKWP2000(connection, fastInit = false)
            
            // 5-baud init sends address at 5 baud, then waits for sync pattern
            // For ELM327: ATSP4 sets KWP 5-baud
            
            // The adapter handles the slow init, we just check for response
            val request = byteArrayOf(0x01, 0x00) // Mode 01 PID 00
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs * 2) // Longer timeout for slow init
            
            if (response != null && isValidKWPResponse(response)) {
                logger.debug("KWP2000 Slow Init: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures KWP2000 protocol parameters.
     */
    private suspend fun configureKWP2000(connection: ScannerConnection, fastInit: Boolean) {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            if (fastInit) "ATSP5" else "ATSP4",
            "ATH1",
            "ATST64" // Set timeout
        )
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    /**
     * Tests ISO 9141-2 protocol.
     */
    private suspend fun testISO9141Protocol(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing ISO 9141-2")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for ISO 9141-2
            configureISO9141(connection)
            
            // ISO 9141 uses 5-baud init similar to KWP slow init
            // For ELM327: ATSP3 sets ISO 9141-2
            
            val request = byteArrayOf(0x01, 0x00) // Mode 01 PID 00
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs * 2)
            
            if (response != null && isValidISO9141Response(response)) {
                logger.debug("ISO 9141-2: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures ISO 9141-2 protocol parameters.
     */
    private suspend fun configureISO9141(connection: ScannerConnection) {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            "ATSP3", // ISO 9141-2
            "ATH1",
            "ATST96" // Longer timeout for slow init
        )
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    /**
     * Tests J1850 protocol (VPW or PWM).
     */
    private suspend fun testJ1850Protocol(
        connection: ScannerConnection,
        protocol: ProtocolType,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing J1850: $protocol")
        
        return when (protocol) {
            ProtocolType.SAE_J1850_VPW -> testJ1850VPW(connection, config)
            ProtocolType.SAE_J1850_PWM -> testJ1850PWM(connection, config)
            else -> false
        }
    }
    
    /**
     * Tests J1850 VPW (Variable Pulse Width) - GM vehicles.
     */
    private suspend fun testJ1850VPW(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing J1850 VPW")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for J1850 VPW
            configureJ1850VPW(connection)
            
            val request = byteArrayOf(0x01, 0x00) // Mode 01 PID 00
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs)
            
            if (response != null && isValidJ1850Response(response)) {
                logger.debug("J1850 VPW: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures J1850 VPW protocol.
     */
    private suspend fun configureJ1850VPW(connection: ScannerConnection) {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            "ATSP2", // J1850 VPW
            "ATH1"
        )
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    /**
     * Tests J1850 PWM (Pulse Width Modulation) - Ford vehicles.
     */
    private suspend fun testJ1850PWM(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing J1850 PWM")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for J1850 PWM
            configureJ1850PWM(connection)
            
            val request = byteArrayOf(0x01, 0x00) // Mode 01 PID 00
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs)
            
            if (response != null && isValidJ1850Response(response)) {
                logger.debug("J1850 PWM: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures J1850 PWM protocol.
     */
    private suspend fun configureJ1850PWM(connection: ScannerConnection) {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            "ATSP1", // J1850 PWM
            "ATH1"
        )
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    /**
     * Tests J1939 protocol for heavy-duty vehicles.
     */
    private suspend fun testJ1939Protocol(
        connection: ScannerConnection,
        config: DetectionConfig
    ): Boolean {
        logger.debug("Testing J1939")
        
        return executeWithRetry(config.retriesPerProtocol) {
            // Configure for J1939
            configureJ1939(connection)
            
            // J1939 uses different addressing and PGNs
            // Request Engine Speed PGN (61444)
            val request = byteArrayOf(
                0x18.toByte(), 0xEA.toByte(), 0x00.toByte(), 0xF9.toByte(), // CAN ID
                0x03, // DLC
                0x00.toByte(), 0xF0.toByte(), 0x04.toByte() // PGN request
            )
            
            val response = sendTestMessage(connection, request, config.testTimeoutMs)
            
            if (response != null && isValidJ1939Response(response)) {
                logger.debug("J1939: Valid response received")
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Configures J1939 protocol.
     */
    private suspend fun configureJ1939(connection: ScannerConnection) {
        val commands = listOf(
            "ATZ",
            "ATE0",
            "ATL0",
            "ATSP6", // CAN 29-bit 250K (J1939)
            "ATH1",
            "ATCAF0" // CAN auto formatting off
        )
        
        for (command in commands) {
            try {
                connection.writeBytes("$command\r".toByteArray()).getOrNull()
                delay(50)
                connection.readBytes(200).getOrNull()
            } catch (e: Exception) {
                logger.debug("Command $command failed: ${e.message}")
            }
        }
    }
    
    // ==================== Response Validation ====================
    
    /**
     * Validates an OBD-II response.
     */
    private fun isValidOBDResponse(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        
        // Convert to string and check for valid response patterns
        val responseStr = String(response, Charsets.US_ASCII).trim()
        
        // Check for error responses
        if (responseStr.contains("NO DATA") ||
            responseStr.contains("UNABLE TO CONNECT") ||
            responseStr.contains("ERROR") ||
            responseStr.contains("?") ||
            responseStr.contains("CAN ERROR")) {
            return false
        }
        
        // Check for valid Mode 01 PID 00 response (41 00 XX XX XX XX)
        val hexPattern = Regex("[0-9A-Fa-f]{2}\\s*")
        val cleanResponse = responseStr.replace(Regex("[^0-9A-Fa-f\\s]"), "").trim()
        
        if (cleanResponse.isEmpty()) return false
        
        // Parse hex bytes
        val bytes = try {
            cleanResponse.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { it.toInt(16).toByte() }
                .toByteArray()
        } catch (e: Exception) {
            return false
        }
        
        // Valid response should start with 41 (Mode 01 + 0x40)
        if (bytes.isNotEmpty() && bytes[0] == 0x41.toByte()) {
            return true
        }
        
        // Check for response with header (e.g., 7E8 06 41 00 XX XX XX XX)
        if (bytes.size >= 3 && bytes.contains(0x41.toByte())) {
            return true
        }
        
        return false
    }
    
    /**
     * Validates a KWP2000 response.
     */
    private fun isValidKWPResponse(response: ByteArray): Boolean {
        if (response.size < 4) return false
        
        val responseStr = String(response, Charsets.US_ASCII).trim()
        
        if (responseStr.contains("NO DATA") ||
            responseStr.contains("ERROR") ||
            responseStr.contains("?")) {
            return false
        }
        
        // Check for valid KWP response format
        // Positive response to Start Communication: C1 33 F1 81 + checksum
        // or Mode 01 response: 41 00 XX XX XX XX
        
        return responseStr.contains("41") || responseStr.contains("C1")
    }
    
    /**
     * Validates an ISO 9141-2 response.
     */
    private fun isValidISO9141Response(response: ByteArray): Boolean {
        return isValidKWPResponse(response) // Similar validation
    }
    
    /**
     * Validates a J1850 response.
     */
    private fun isValidJ1850Response(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        
        val responseStr = String(response, Charsets.US_ASCII).trim()
        
        if (responseStr.contains("NO DATA") ||
            responseStr.contains("ERROR") ||
            responseStr.contains("?") ||
            responseStr.contains("BUS INIT")) {
            return false
        }
        
        // Check for valid J1850 response (Mode 01 response: 41 00 XX XX XX XX)
        return responseStr.contains("41")
    }
    
    /**
     * Validates a J1939 response.
     */
    private fun isValidJ1939Response(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        
        val responseStr = String(response, Charsets.US_ASCII).trim()
        
        if (responseStr.contains("NO DATA") ||
            responseStr.contains("ERROR") ||
            responseStr.contains("?")) {
            return false
        }
        
        // J1939 responses have specific PGN patterns
        return response.size >= 8
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Sends a test message and returns the response.
     */
    private suspend fun sendTestMessage(
        connection: ScannerConnection,
        request: ByteArray,
        timeoutMs: Long
    ): ByteArray? {
        return try {
            // Format request for ELM327 (hex string with CR)
            val hexRequest = request.joinToString("") { "%02X".format(it) } + "\r"
            
            connection.writeBytes(hexRequest.toByteArray()).getOrNull()
            
            withTimeoutOrNull(timeoutMs) {
                // Read response with small delays to collect complete response
                val buffer = StringBuilder()
                var attempts = 0
                val maxAttempts = (timeoutMs / 50).toInt()
                
                while (attempts < maxAttempts) {
                    val chunk = connection.readBytes(100).getOrNull() ?: ByteArray(0)
                    if (chunk.isNotEmpty()) {
                        buffer.append(String(chunk, Charsets.US_ASCII))
                        
                        // Check if response is complete (ends with > prompt)
                        if (buffer.contains(">")) {
                            break
                        }
                    }
                    attempts++
                    delay(50)
                }
                
                buffer.toString().toByteArray()
            }
        } catch (e: Exception) {
            logger.debug("Send test message failed: ${e.message}")
            null
        }
    }
    
    /**
     * Executes with retry logic.
     */
    private suspend fun executeWithRetry(
        maxRetries: Int,
        block: suspend () -> Boolean
    ): Boolean {
        repeat(maxRetries) { attempt ->
            if (isCancelled.get()) return false
            
            try {
                if (block()) return true
            } catch (e: Exception) {
                logger.debug("Retry ${attempt + 1}/$maxRetries failed: ${e.message}")
            }
            
            if (attempt < maxRetries - 1) {
                delay(100)
            }
        }
        return false
    }
    
    companion object {
        private const val TAG = "ProtocolDetector"
        
        // CAN broadcast addresses
        private const val OBD_BROADCAST_11BIT = 0x7DF
        private const val OBD_BROADCAST_29BIT = 0x18DB33F1
        
        // Vehicle manufacturer categories
        private val GM_MAKES = setOf(
            "CHEVROLET", "CHEVY", "GMC", "BUICK", "CADILLAC", 
            "PONTIAC", "SATURN", "OLDSMOBILE", "HUMMER"
        )
        
        private val FORD_MAKES = setOf(
            "FORD", "LINCOLN", "MERCURY"
        )
        
        private val EUROPEAN_MAKES = setOf(
            "BMW", "MERCEDES", "MERCEDES-BENZ", "AUDI", "VOLKSWAGEN", "VW",
            "PORSCHE", "VOLVO", "SAAB", "OPEL", "VAUXHALL", "PEUGEOT",
            "CITROEN", "RENAULT", "FIAT", "ALFA ROMEO", "LANCIA", "SEAT",
            "SKODA", "MINI", "JAGUAR", "LAND ROVER", "BENTLEY", "ROLLS-ROYCE"
        )
        
        private val ASIAN_MAKES = setOf(
            "TOYOTA", "LEXUS", "HONDA", "ACURA", "NISSAN", "INFINITI",
            "MAZDA", "MITSUBISHI", "SUBARU", "SUZUKI", "ISUZU", "DAIHATSU",
            "HYUNDAI", "KIA", "DAEWOO", "SSANGYONG"
        )
        
        private val HEAVY_DUTY_MAKES = setOf(
            "FREIGHTLINER", "PETERBILT", "KENWORTH", "MACK", "VOLVO TRUCKS",
            "INTERNATIONAL", "NAVISTAR", "WESTERN STAR", "CATERPILLAR", "CUMMINS"
        )
        
        /**
         * Default protocol detection order.
         */
        val DEFAULT_DETECTION_ORDER = listOf(
            ProtocolType.ISO_15765_4_CAN_11BIT_500K,
            ProtocolType.ISO_15765_4_CAN_29BIT_500K,
            ProtocolType.ISO_15765_4_CAN_11BIT_250K,
            ProtocolType.ISO_15765_4_CAN_29BIT_250K,
            ProtocolType.ISO_14230_4_KWP_FAST,
            ProtocolType.ISO_9141_2,
            ProtocolType.SAE_J1850_VPW,
            ProtocolType.SAE_J1850_PWM
        )
    }
}

// ==================== Detection Configuration ====================

/**
 * Configuration options for protocol detection.
 * 
 * @property testTimeoutMs Timeout for each protocol test in milliseconds
 * @property retriesPerProtocol Number of retries for each protocol
 * @property retryDelayMs Delay between retries in milliseconds
 * @property stopOnFirstMatch Whether to stop after first successful detection
 * @property skipProtocols Set of protocols to skip during detection
 * @property preferredProtocol Protocol to test first (if known)
 * @property totalTimeoutMs Overall detection timeout in milliseconds
 */
data class DetectionConfig(
    val testTimeoutMs: Long = DEFAULT_TEST_TIMEOUT,
    val retriesPerProtocol: Int = DEFAULT_RETRIES,
    val retryDelayMs: Long = DEFAULT_RETRY_DELAY,
    val stopOnFirstMatch: Boolean = true,
    val skipProtocols: Set<ProtocolType> = emptySet(),
    val preferredProtocol: ProtocolType? = null,
    val totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT
) {
    /**
     * Builder for DetectionConfig.
     */
    class Builder {
        private var testTimeoutMs: Long = DEFAULT_TEST_TIMEOUT
        private var retriesPerProtocol: Int = DEFAULT_RETRIES
        private var retryDelayMs: Long = DEFAULT_RETRY_DELAY
        private var stopOnFirstMatch: Boolean = true
        private var skipProtocols: Set<ProtocolType> = emptySet()
        private var preferredProtocol: ProtocolType? = null
        private var totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT
        
        fun testTimeout(ms: Long) = apply { testTimeoutMs = ms }
        fun retries(count: Int) = apply { retriesPerProtocol = count }
        fun retryDelay(ms: Long) = apply { retryDelayMs = ms }
        fun stopOnFirst(stop: Boolean) = apply { stopOnFirstMatch = stop }
        fun skip(protocols: Set<ProtocolType>) = apply { skipProtocols = protocols }
        fun prefer(protocol: ProtocolType) = apply { preferredProtocol = protocol }
        fun totalTimeout(ms: Long) = apply { totalTimeoutMs = ms }
        
        fun build() = DetectionConfig(
            testTimeoutMs = testTimeoutMs,
            retriesPerProtocol = retriesPerProtocol,
            retryDelayMs = retryDelayMs,
            stopOnFirstMatch = stopOnFirstMatch,
            skipProtocols = skipProtocols,
            preferredProtocol = preferredProtocol,
            totalTimeoutMs = totalTimeoutMs
        )
    }
    
    companion object {
        private const val DEFAULT_TEST_TIMEOUT = 3000L
        private const val DEFAULT_RETRIES = 2
        private const val DEFAULT_RETRY_DELAY = 100L
        private const val DEFAULT_TOTAL_TIMEOUT = 30000L
        
        /** Default configuration for general use. */
        val DEFAULT = DetectionConfig()
        
        /** Fast detection for known modern vehicles. */
        val FAST = DetectionConfig(
            testTimeoutMs = 1500L,
            retriesPerProtocol = 1,
            totalTimeoutMs = 15000L
        )
        
        /** Thorough detection that tests all protocols. */
        val THOROUGH = DetectionConfig(
            testTimeoutMs = 5000L,
            retriesPerProtocol = 3,
            stopOnFirstMatch = false,
            totalTimeoutMs = 60000L
        )
        
        /** Detection optimized for CAN-only vehicles (2008+). */
        val CAN_ONLY = DetectionConfig(
            testTimeoutMs = 2000L,
            retriesPerProtocol = 2,
            skipProtocols = setOf(
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM
            ),
            totalTimeoutMs = 15000L
        )
        
        /** Creates a builder for custom configuration. */
        fun builder(): Builder = Builder()
    }
}

// ==================== Detection Progress ====================

/**
 * Represents progress during protocol detection.
 */
sealed class DetectionProgress {
    
    /** Detection has started. */
    object Started : DetectionProgress() {
        override fun toString() = "Started"
    }
    
    /**
     * Currently testing a protocol.
     * 
     * @property protocol Protocol being tested
     * @property protocolIndex Current protocol index (1-based)
     * @property totalProtocols Total number of protocols to test
     * @property progress Progress as a fraction (0.0 to 1.0)
     */
    data class Testing(
        val protocol: ProtocolType,
        val protocolIndex: Int,
        val totalProtocols: Int,
        val progress: Float
    ) : DetectionProgress() {
        val progressPercent: Int get() = (progress * 100).toInt()
    }
    
    /**
     * A protocol test has completed.
     * 
     * @property protocol Protocol that was tested
     * @property success Whether the test was successful
     * @property responseTime Response time in milliseconds
     */
    data class TestedProtocol(
        val protocol: ProtocolType,
        val success: Boolean,
        val responseTime: Long
    ) : DetectionProgress()
    
    /**
     * Protocol successfully detected.
     * 
     * @property protocol Detected protocol type
     * @property detectionTime Total detection time in milliseconds
     */
    data class Detected(
        val protocol: ProtocolType,
        val detectionTime: Long
    ) : DetectionProgress()
    
    /**
     * Detection failed.
     * 
     * @property error The error that occurred
     * @property testedProtocols List of protocols that were tested
     */
    data class Failed(
        val error: ProtocolError,
        val testedProtocols: List<ProtocolType>
    ) : DetectionProgress()
    
    /** Detection was cancelled. */
    object Cancelled : DetectionProgress() {
        override fun toString() = "Cancelled"
    }
}

// ==================== Detection State ====================

/**
 * Current state of the protocol detector.
 */
sealed class DetectionState {
    
    /** Detector is idle. */
    object Idle : DetectionState()
    
    /**
     * Detection is in progress.
     * 
     * @property progress Progress as a fraction (0.0 to 1.0)
     * @property currentProtocol Protocol currently being tested
     */
    data class Detecting(
        val progress: Float,
        val currentProtocol: ProtocolType?
    ) : DetectionState()
    
    /**
     * Protocol was detected.
     * 
     * @property protocol Detected protocol
     * @property detectionTimeMs Detection time in milliseconds
     */
    data class Detected(
        val protocol: ProtocolType,
        val detectionTimeMs: Long
    ) : DetectionState()
    
    /**
     * Detection failed.
     * 
     * @property error The error that occurred
     * @property testedProtocols Protocols that were tested
     */
    data class Failed(
        val error: Throwable,
        val testedProtocols: List<ProtocolType>
    ) : DetectionState()
    
    /** Detection was cancelled. */
    object Cancelled : DetectionState()
}

// ==================== Detection Result ====================

/**
 * Complete result of a protocol detection operation.
 * 
 * @property detectedProtocol The detected protocol (null if none)
 * @property testedProtocols Map of all tested protocols and their results
 * @property detectionTimeMs Total detection time in milliseconds
 * @property success Whether detection was successful
 */
data class DetectionResult(
    val detectedProtocol: ProtocolType?,
    val testedProtocols: Map<ProtocolType, ProtocolTestResult>,
    val detectionTimeMs: Long,
    val success: Boolean
) {
    /** All protocols that responded successfully. */
    val respondingProtocols: List<ProtocolType>
        get() = testedProtocols.filter { it.value.success }.keys.toList()
    
    /** Fastest responding protocol. */
    val fastestProtocol: ProtocolType?
        get() = testedProtocols
            .filter { it.value.success }
            .minByOrNull { it.value.responseTimeMs }
            ?.key
}

/**
 * Result of testing a single protocol.
 * 
 * @property protocol The protocol that was tested
 * @property success Whether the test was successful
 * @property responseTimeMs Response time in milliseconds
 * @property errorMessage Error message if test failed
 */
data class ProtocolTestResult(
    val protocol: ProtocolType,
    val success: Boolean,
    val responseTimeMs: Long,
    val errorMessage: String? = null
)

// ==================== Protocol Error ====================

/**
 * Errors that can occur during protocol detection.
 */
sealed class ProtocolError(
    open val message: String
) {
    /** No compatible protocol was found. */
    data class NoProtocolFound(
        override val message: String
    ) : ProtocolError(message)
    
    /** Detection timed out. */
    data class Timeout(
        override val message: String
    ) : ProtocolError(message)
    
    /** Communication error during detection. */
    data class CommunicationError(
        override val message: String
    ) : ProtocolError(message)
    
    /** Connection error. */
    data class ConnectionError(
        override val message: String
    ) : ProtocolError(message)
}

// ==================== Protocol Type Extensions ====================

/**
 * Protocol category for grouping related protocols.
 */
enum class ProtocolCategory {
    CAN,
    KWP2000,
    ISO9141,
    J1850,
    J1939
}

/**
 * Gets the category for a protocol type.
 */
val ProtocolType.category: ProtocolCategory
    get() = when (this) {
        ProtocolType.ISO_15765_4_CAN_11BIT_500K,
        ProtocolType.ISO_15765_4_CAN_29BIT_500K,
        ProtocolType.ISO_15765_4_CAN_11BIT_250K,
        ProtocolType.ISO_15765_4_CAN_29BIT_250K -> ProtocolCategory.CAN
        
        ProtocolType.ISO_14230_4_KWP_FAST -> ProtocolCategory.KWP2000
        
        ProtocolType.ISO_9141_2 -> ProtocolCategory.ISO9141
        
        ProtocolType.SAE_J1850_VPW,
        ProtocolType.SAE_J1850_PWM -> ProtocolCategory.J1850
        
        else -> ProtocolCategory.CAN // Default to CAN for unknown
    }
