/**
 * File: ScannerRepositoryImpl.kt
 * 
 * Implementation of the ScannerRepository interface for the SpaceTec automotive
 * diagnostic application. This class manages scanner discovery, connection,
 * and communication across all supported connection types including Bluetooth,
 * WiFi, and USB.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. ScannerRepositoryImpl class with full implementation
 * 3. DiscoveryResult sealed class
 * 4. ScannerInfo and ScannerCapabilities data classes
 * 5. FirmwareUpdateInfo data class
 * 6. Supporting classes and extension functions
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.data.repository

import android.content.Context
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ConnectionException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.database.dao.ScannerDao
import com.spacetec.core.database.entities.ScannerEntity
import com.spacetec.core.logging.DiagnosticLogger
import com.spacetec.data.datasource.local.ScannerLocalDataSource
import com.spacetec.data.mapper.ScannerMapper
import com.spacetec.obd.core.domain.models.scanner.ConnectionState
import com.spacetec.obd.core.domain.models.scanner.Scanner
import com.spacetec.obd.core.domain.models.scanner.ScannerType
import com.spacetec.domain.repository.ScannerRepository
import com.spacetec.protocol.core.ProtocolType
import com.spacetec.scanner.bluetooth.BluetoothDiscovery
import com.spacetec.scanner.bluetooth.BluetoothScanner
import com.spacetec.scanner.core.ScannerConnection
import com.spacetec.scanner.core.ScannerDiscovery
import com.spacetec.scanner.core.ScannerManager
import com.spacetec.scanner.usb.USBDiscovery
import com.spacetec.scanner.usb.USBScanner
import com.spacetec.scanner.wifi.WiFiDiscovery
import com.spacetec.scanner.wifi.WiFiScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ScannerRepository] that manages scanner discovery,
 * connection, and communication.
 * 
 * This class provides a unified interface for working with OBD-II scanners
 * regardless of their connection type (Bluetooth, WiFi, USB). It handles:
 * 
 * - **Discovery**: Finding available scanners across all connection types
 * - **Connection Management**: Connecting, disconnecting, and reconnecting
 * - **State Tracking**: Monitoring connection state and health
 * - **Communication**: Sending commands and receiving responses
 * - **Persistence**: Saving scanner configurations and history
 * 
 * ## Connection Flow
 * 
 * ```
 * Discovery → Select Scanner → Connect → Communicate → Disconnect
 *                                  ↓
 *                            Auto-reconnect (if enabled)
 * ```
 * 
 * ## Thread Safety
 * 
 * All operations are thread-safe through:
 * - [Mutex] for connection state synchronization
 * - [StateFlow] for reactive state updates
 * - Proper dispatcher usage for I/O operations
 * 
 * ## Usage Example
 * 
 * ```kotlin
 * // Discover scanners
 * scannerRepository.discoverScanners().collect { result ->
 *     when (result) {
 *         is DiscoveryResult.Found -> addScanner(result.scanner)
 *         is DiscoveryResult.Completed -> stopLoading()
 *     }
 * }
 * 
 * // Connect to a scanner
 * val connection = scannerRepository.connect(scanner).getOrThrow()
 * 
 * // Send commands
 * val response = scannerRepository.sendCommand("ATZ").getOrThrow()
 * ```
 * 
 * @property scannerManager Core scanner management
 * @property bluetoothDiscovery Bluetooth scanner discovery
 * @property wifiDiscovery WiFi scanner discovery
 * @property usbDiscovery USB scanner discovery
 * @property scannerLocalDataSource Local data source for scanner storage
 * @property scannerDao DAO for scanner persistence
 * @property scannerMapper Mapper for entity transformations
 * @property ioDispatcher Dispatcher for I/O operations
 * 
 * @see ScannerRepository
 * @see ScannerManager
 * @see ScannerConnection
 */
@Singleton
class ScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scannerManager: ScannerManager,
    private val bluetoothDiscovery: BluetoothDiscovery,
    private val wifiDiscovery: WiFiDiscovery,
    private val usbDiscovery: USBDiscovery,
    private val scannerLocalDataSource: ScannerLocalDataSource,
    private val scannerDao: ScannerDao,
    private val scannerMapper: ScannerMapper,
    private val logger: DiagnosticLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ScannerRepository {

    // ==================== Private Properties ====================

    /**
     * Coroutine scope for repository operations.
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * Mutex for synchronizing connection operations.
     */
    private val connectionMutex = Mutex()

    /**
     * Mutex for synchronizing discovery operations.
     */
    private val discoveryMutex = Mutex()

    /**
     * Current connection state.
     */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /**
     * Currently connected scanner.
     */
    private val _connectedScanner = MutableStateFlow<Scanner?>(null)

    /**
     * Active scanner connection.
     */
    private var activeConnection: ScannerConnection? = null

    /**
     * Discovered scanners cache.
     */
    private val discoveredScanners = ConcurrentHashMap<String, Scanner>()

    /**
     * Discovery flow for emitting results.
     */
    private val _discoveryResults = MutableSharedFlow<DiscoveryResult>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Incoming data flow.
     */
    private val _incomingData = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Signal strength flow.
     */
    private val _signalStrength = MutableStateFlow<Int>(0)

    /**
     * Battery voltage flow.
     */
    private val _voltage = MutableStateFlow<Float>(0f)

    /**
     * Current discovery job.
     */
    private var discoveryJob: Job? = null

    /**
     * Health monitoring job.
     */
    private var healthMonitorJob: Job? = null

    /**
     * Auto-reconnect enabled flag.
     */
    private val autoReconnectEnabled = AtomicBoolean(true)

    /**
     * Reconnect attempt counter.
     */
    private var reconnectAttempts = 0

    /**
     * Maximum reconnect attempts.
     */
    private val maxReconnectAttempts = 3

    /**
     * Reconnect delay in milliseconds.
     */
    private val reconnectDelayMs = 2000L

    // ==================== Public Properties ====================

    /**
     * Current connection state as observable StateFlow.
     */
    override val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    /**
     * Currently connected scanner as observable StateFlow.
     */
    override val connectedScanner: StateFlow<Scanner?>
        get() = _connectedScanner.asStateFlow()

    /**
     * Whether currently connected to a scanner.
     */
    override val isConnected: Boolean
        get() = _connectionState.value == ConnectionState.Connected &&
                activeConnection?.isConnected == true

    // ==================== Initialization ====================

    init {
        // Initialize scanner manager callbacks
        setupScannerManagerCallbacks()
        
        // Start health monitoring
        startHealthMonitoring()
        
        logger.info(TAG, "ScannerRepository initialized")
    }

    /**
     * Sets up callbacks for scanner manager events.
     */
    private fun setupScannerManagerCallbacks() {
        repositoryScope.launch {
            scannerManager.connectionEvents.collect { event ->
                when (event) {
                    is ScannerManager.ConnectionEvent.Connected -> {
                        _connectionState.value = ConnectionState.Connected
                        reconnectAttempts = 0
                    }
                    is ScannerManager.ConnectionEvent.Disconnected -> {
                        handleDisconnection(event.reason)
                    }
                    is ScannerManager.ConnectionEvent.Error -> {
                        handleConnectionError(event.error)
                    }
                    is ScannerManager.ConnectionEvent.DataReceived -> {
                        _incomingData.emit(event.data)
                    }
                }
            }
        }
    }

    /**
     * Starts health monitoring coroutine.
     */
    private fun startHealthMonitoring() {
        healthMonitorJob?.cancel()
        healthMonitorJob = repositoryScope.launch {
            while (isActive) {
                if (isConnected) {
                    try {
                        updateHealthMetrics()
                    } catch (e: Exception) {
                        logger.warn(TAG, "Health monitoring error: ${e.message}")
                    }
                }
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Updates connection health metrics.
     */
    private suspend fun updateHealthMetrics() {
        // Update signal strength if available
        activeConnection?.getSignalStrength()?.let { signal ->
            _signalStrength.value = signal
        }

        // Update voltage if available
        try {
            val voltageResponse = sendCommandInternal("ATRV", 500)
            parseVoltage(voltageResponse)?.let { voltage ->
                _voltage.value = voltage
            }
        } catch (e: Exception) {
            // Ignore voltage read errors
        }
    }

    // ==================== Discovery Operations ====================

    /**
     * Discovers available scanners of the specified types.
     * 
     * This method starts discovery for all requested scanner types
     * simultaneously and emits results as scanners are found.
     * 
     * @param types Set of scanner types to discover (default: all types)
     * @return Flow emitting discovery results
     */
    override fun discoverScanners(
        types: Set<ScannerType>
    ): Flow<DiscoveryResult> = _discoveryResults.asSharedFlow()
        .onStart {
            startDiscovery(types)
        }
        .flowOn(ioDispatcher)

    /**
     * Starts scanner discovery for specified types.
     */
    private fun startDiscovery(types: Set<ScannerType>) {
        discoveryJob?.cancel()
        discoveredScanners.clear()
        
        discoveryJob = repositoryScope.launch {
            logger.info(TAG, "Starting scanner discovery for types: $types")
            _discoveryResults.emit(DiscoveryResult.Started)
            
            try {
                val discoveryFlows = mutableListOf<Flow<DiscoveryResult>>()
                
                if (ScannerType.BLUETOOTH in types || ScannerType.BLUETOOTH_LE in types) {
                    discoveryFlows.add(discoverBluetoothScannersFlow())
                }
                
                if (ScannerType.WIFI in types) {
                    discoveryFlows.add(discoverWiFiScannersFlow())
                }
                
                if (ScannerType.USB in types) {
                    discoveryFlows.add(discoverUSBScannersFlow())
                }
                
                // Merge all discovery flows
                if (discoveryFlows.isNotEmpty()) {
                    merge(*discoveryFlows.toTypedArray()).collect { result ->
                        _discoveryResults.emit(result)
                        
                        if (result is DiscoveryResult.Found) {
                            discoveredScanners[result.scanner.id] = result.scanner
                        }
                    }
                }
                
                _discoveryResults.emit(DiscoveryResult.Completed)
                logger.info(TAG, "Discovery completed. Found ${discoveredScanners.size} scanners")
                
            } catch (e: CancellationException) {
                logger.info(TAG, "Discovery cancelled")
                throw e
            } catch (e: Exception) {
                logger.error(TAG, "Discovery error", e)
                _discoveryResults.emit(DiscoveryResult.error(e))
            }
        }
    }

    /**
     * Creates a flow for Bluetooth scanner discovery.
     */
    private fun discoverBluetoothScannersFlow(): Flow<DiscoveryResult> = 
        bluetoothDiscovery.discover()
            .map { bluetoothDevice ->
                val scanner = Scanner(
                    id = bluetoothDevice.address,
                    name = bluetoothDevice.name ?: "Unknown Bluetooth Scanner",
                    type = if (bluetoothDevice.isLE) ScannerType.BLUETOOTH_LE else ScannerType.BLUETOOTH,
                    address = bluetoothDevice.address,
                    isPaired = bluetoothDevice.isPaired,
                    signalStrength = bluetoothDevice.rssi,
                    lastSeen = System.currentTimeMillis()
                )
                DiscoveryResult.Found(scanner)
            }
            .catch { e ->
                logger.error(TAG, "Bluetooth discovery error", e)
                emit(DiscoveryResult.error(e))
            }

    /**
     * Creates a flow for WiFi scanner discovery.
     */
    private fun discoverWiFiScannersFlow(): Flow<DiscoveryResult> = 
        wifiDiscovery.discover()
            .map { wifiDevice ->
                val scanner = Scanner(
                    id = wifiDevice.ipAddress,
                    name = wifiDevice.name ?: "WiFi Scanner",
                    type = ScannerType.WIFI,
                    address = wifiDevice.ipAddress,
                    port = wifiDevice.port,
                    signalStrength = wifiDevice.rssi,
                    lastSeen = System.currentTimeMillis()
                )
                DiscoveryResult.Found(scanner)
            }
            .catch { e ->
                logger.error(TAG, "WiFi discovery error", e)
                emit(DiscoveryResult.error(e))
            }

    /**
     * Creates a flow for USB scanner discovery.
     */
    private fun discoverUSBScannersFlow(): Flow<DiscoveryResult> = 
        usbDiscovery.discover()
            .map { usbDevice ->
                val scanner = Scanner(
                    id = usbDevice.deviceId.toString(),
                    name = usbDevice.productName ?: "USB Scanner",
                    type = ScannerType.USB,
                    address = usbDevice.deviceName,
                    vendorId = usbDevice.vendorId,
                    productId = usbDevice.productId,
                    lastSeen = System.currentTimeMillis()
                )
                DiscoveryResult.Found(scanner)
            }
            .catch { e ->
                logger.error(TAG, "USB discovery error", e)
                emit(DiscoveryResult.error(e))
            }

    /**
     * Discovers Bluetooth scanners.
     * 
     * @return Result containing list of discovered Bluetooth scanners
     */
    override suspend fun discoverBluetoothScanners(): Result<List<Scanner>> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Discovering Bluetooth scanners")
                
                val scanners = mutableListOf<Scanner>()
                
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    bluetoothDiscovery.discover().collect { device ->
                        val scanner = Scanner(
                            id = device.address,
                            name = device.name ?: "Bluetooth Scanner",
                            type = if (device.isLE) ScannerType.BLUETOOTH_LE else ScannerType.BLUETOOTH,
                            address = device.address,
                            isPaired = device.isPaired,
                            signalStrength = device.rssi,
                            lastSeen = System.currentTimeMillis()
                        )
                        scanners.add(scanner)
                        discoveredScanners[scanner.id] = scanner
                    }
                }
                
                logger.info(TAG, "Found ${scanners.size} Bluetooth scanners")
                Result.success(scanners)
                
            } catch (e: Exception) {
                logger.error(TAG, "Bluetooth discovery failed", e)
                Result.failure(e)
            }
        }

    /**
     * Discovers WiFi scanners.
     * 
     * @return Result containing list of discovered WiFi scanners
     */
    override suspend fun discoverWiFiScanners(): Result<List<Scanner>> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Discovering WiFi scanners")
                
                val scanners = mutableListOf<Scanner>()
                
                withTimeout(DISCOVERY_TIMEOUT_MS) {
                    wifiDiscovery.discover().collect { device ->
                        val scanner = Scanner(
                            id = device.ipAddress,
                            name = device.name ?: "WiFi Scanner",
                            type = ScannerType.WIFI,
                            address = device.ipAddress,
                            port = device.port,
                            signalStrength = device.rssi,
                            lastSeen = System.currentTimeMillis()
                        )
                        scanners.add(scanner)
                        discoveredScanners[scanner.id] = scanner
                    }
                }
                
                logger.info(TAG, "Found ${scanners.size} WiFi scanners")
                Result.success(scanners)
                
            } catch (e: Exception) {
                logger.error(TAG, "WiFi discovery failed", e)
                Result.failure(e)
            }
        }

    /**
     * Discovers USB scanners.
     * 
     * @return Result containing list of discovered USB scanners
     */
    override suspend fun discoverUSBScanners(): Result<List<Scanner>> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Discovering USB scanners")
                
                val scanners = mutableListOf<Scanner>()
                
                usbDiscovery.discover().collect { device ->
                    val scanner = Scanner(
                        id = device.deviceId.toString(),
                        name = device.productName ?: "USB Scanner",
                        type = ScannerType.USB,
                        address = device.deviceName,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        lastSeen = System.currentTimeMillis()
                    )
                    scanners.add(scanner)
                    discoveredScanners[scanner.id] = scanner
                }
                
                logger.info(TAG, "Found ${scanners.size} USB scanners")
                Result.success(scanners)
                
            } catch (e: Exception) {
                logger.error(TAG, "USB discovery failed", e)
                Result.failure(e)
            }
        }

    /**
     * Observes discovered scanners.
     * 
     * @return Flow emitting list of currently discovered scanners
     */
    override fun observeDiscoveredScanners(): Flow<List<Scanner>> = 
        _discoveryResults.asSharedFlow()
            .filter { it is DiscoveryResult.Found || it is DiscoveryResult.Lost }
            .map { discoveredScanners.values.toList() }
            .distinctUntilChanged()

    /**
     * Stops ongoing discovery.
     */
    override suspend fun stopDiscovery() {
        logger.info(TAG, "Stopping discovery")
        discoveryJob?.cancel()
        discoveryJob = null
        
        bluetoothDiscovery.stopDiscovery()
        wifiDiscovery.stopDiscovery()
    }

    // ==================== Connection Operations ====================

    /**
     * Connects to a scanner.
     * 
     * @param scanner Scanner to connect to
     * @return Result containing the active connection
     */
    override suspend fun connect(scanner: Scanner): Result<ScannerConnection> = 
        connectionMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    logger.info(TAG, "Connecting to scanner: ${scanner.name} (${scanner.id})")
                    
                    // Disconnect existing connection if any
                    if (activeConnection?.isConnected == true) {
                        logger.info(TAG, "Disconnecting from current scanner")
                        disconnectInternal()
                    }
                    
                    _connectionState.value = ConnectionState.Connecting
                    
                    // Create appropriate connection based on scanner type
                    val connection = when (scanner.type) {
                        ScannerType.BLUETOOTH, ScannerType.BLUETOOTH_LE -> {
                            createBluetoothConnection(scanner)
                        }
                        ScannerType.WIFI -> {
                            createWiFiConnection(scanner)
                        }
                        ScannerType.USB -> {
                            createUSBConnection(scanner)
                        }
                    }
                    
                    // Attempt connection with timeout
                    withTimeout(CONNECTION_TIMEOUT_MS) {
                        connection.connect()
                    }
                    
                    if (!connection.isConnected) {
                        throw ConnectionException("Failed to establish connection")
                    }
                    
                    // Initialize the adapter
                    initializeAdapter(connection)
                    
                    // Store connection
                    activeConnection = connection
                    _connectedScanner.value = scanner
                    _connectionState.value = ConnectionState.Connected
                    
                    // Update scanner in database
                    updateScannerLastConnected(scanner)
                    
                    // Start data receiver
                    startDataReceiver(connection)
                    
                    logger.info(TAG, "Successfully connected to ${scanner.name}")
                    Result.success(connection)
                    
                } catch (e: CancellationException) {
                    _connectionState.value = ConnectionState.Disconnected
                    throw e
                } catch (e: Exception) {
                    logger.error(TAG, "Connection failed", e)
                    _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
                    Result.failure(ConnectionException("Failed to connect: ${e.message}", e))
                }
            }
        }

    /**
     * Connects to a scanner by ID.
     * 
     * @param scannerId Scanner ID to connect to
     * @return Result containing the active connection
     */
    override suspend fun connect(scannerId: String): Result<ScannerConnection> = 
        withContext(ioDispatcher) {
            // Check discovered scanners first
            val scanner = discoveredScanners[scannerId]
                ?: scannerDao.getScanner(scannerId)?.let { scannerMapper.toDomain(it) }
                ?: return@withContext Result.failure(
                    ConnectionException("Scanner not found: $scannerId")
                )
            
            connect(scanner)
        }

    /**
     * Creates a Bluetooth connection.
     */
    private suspend fun createBluetoothConnection(scanner: Scanner): ScannerConnection {
        return BluetoothScanner(context).apply {
            setDevice(scanner.address)
        }
    }

    /**
     * Creates a WiFi connection.
     */
    private suspend fun createWiFiConnection(scanner: Scanner): ScannerConnection {
        return WiFiScanner().apply {
            setEndpoint(scanner.address, scanner.port ?: DEFAULT_WIFI_PORT)
        }
    }

    /**
     * Creates a USB connection.
     */
    private suspend fun createUSBConnection(scanner: Scanner): ScannerConnection {
        return USBScanner(context).apply {
            setDevice(scanner.address)
        }
    }

    /**
     * Initializes the OBD adapter with standard AT commands.
     */
    private suspend fun initializeAdapter(connection: ScannerConnection) {
        logger.debug(TAG, "Initializing adapter")
        
        val initCommands = listOf(
            "ATZ" to 1500L,    // Reset
            "ATE0" to 200L,    // Echo off
            "ATL0" to 200L,    // Linefeeds off
            "ATS0" to 200L,    // Spaces off
            "ATH1" to 200L,    // Headers on
            "ATSP0" to 200L    // Auto protocol
        )
        
        for ((command, delay) in initCommands) {
            try {
                connection.write("$command\r".toByteArray())
                delay(delay)
                connection.read(500)
            } catch (e: Exception) {
                logger.warn(TAG, "Init command $command failed: ${e.message}")
            }
        }
        
        logger.debug(TAG, "Adapter initialization complete")
    }

    /**
     * Starts the data receiver coroutine.
     */
    private fun startDataReceiver(connection: ScannerConnection) {
        repositoryScope.launch {
            try {
                while (isActive && connection.isConnected) {
                    val data = connection.readAsync()
                    if (data.isNotEmpty()) {
                        _incomingData.emit(data)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.error(TAG, "Data receiver error", e)
                }
            }
        }
    }

    /**
     * Disconnects from the current scanner.
     * 
     * @return Result indicating success or failure
     */
    override suspend fun disconnect(): Result<Unit> = connectionMutex.withLock {
        withContext(ioDispatcher) {
            try {
                disconnectInternal()
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Disconnect error", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Internal disconnect without lock.
     */
    private suspend fun disconnectInternal() {
        logger.info(TAG, "Disconnecting from scanner")
        
        try {
            activeConnection?.disconnect()
        } catch (e: Exception) {
            logger.warn(TAG, "Error during disconnect: ${e.message}")
        }
        
        activeConnection = null
        _connectedScanner.value = null
        _connectionState.value = ConnectionState.Disconnected
        reconnectAttempts = 0
        
        logger.info(TAG, "Disconnected successfully")
    }

    /**
     * Reconnects to the last connected scanner.
     * 
     * @return Result containing the active connection
     */
    override suspend fun reconnect(): Result<ScannerConnection> = 
        withContext(ioDispatcher) {
            val lastScanner = _connectedScanner.value
                ?: getLastConnectedScanner().getOrNull()
                ?: return@withContext Result.failure(
                    ConnectionException("No scanner to reconnect to")
                )
            
            connect(lastScanner)
        }

    /**
     * Handles disconnection events.
     */
    private suspend fun handleDisconnection(reason: String?) {
        logger.warn(TAG, "Scanner disconnected: $reason")
        
        val wasConnected = _connectionState.value == ConnectionState.Connected
        _connectionState.value = ConnectionState.Disconnected
        
        // Attempt auto-reconnect if enabled
        if (wasConnected && autoReconnectEnabled.get() && reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            logger.info(TAG, "Attempting reconnect ($reconnectAttempts/$maxReconnectAttempts)")
            
            _connectionState.value = ConnectionState.Reconnecting
            delay(reconnectDelayMs)
            
            try {
                reconnect()
            } catch (e: Exception) {
                logger.error(TAG, "Reconnect failed", e)
                if (reconnectAttempts >= maxReconnectAttempts) {
                    _connectionState.value = ConnectionState.Error("Reconnection failed")
                }
            }
        }
    }

    /**
     * Handles connection errors.
     */
    private suspend fun handleConnectionError(error: Throwable) {
        logger.error(TAG, "Connection error", error)
        _connectionState.value = ConnectionState.Error(error.message ?: "Unknown error")
    }

    /**
     * Observes connection state changes.
     * 
     * @return Flow emitting connection state changes
     */
    override fun observeConnectionState(): Flow<ConnectionState> =
        _connectionState.asStateFlow()
            .distinctUntilChanged()

    /**
     * Gets the active connection.
     * 
     * @return Active connection or null
     */
    override suspend fun getConnection(): ScannerConnection? = activeConnection

    // ==================== Scanner Management ====================

    /**
     * Gets saved/paired scanners.
     * 
     * @return Result containing list of saved scanners
     */
    override suspend fun getSavedScanners(): Result<List<Scanner>> = 
        withContext(ioDispatcher) {
            try {
                val entities = scannerDao.getAllScanners()
                val scanners = entities.map { scannerMapper.toDomain(it) }
                Result.success(scanners)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting saved scanners", e)
                Result.failure(e)
            }
        }

    /**
     * Observes saved scanners.
     * 
     * @return Flow emitting list of saved scanners
     */
    override fun observeSavedScanners(): Flow<List<Scanner>> =
        scannerDao.observeAllScanners()
            .map { entities -> entities.map { scannerMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
            .distinctUntilChanged()

    /**
     * Saves a scanner to the database.
     * 
     * @param scanner Scanner to save
     * @return Result indicating success or failure
     */
    override suspend fun saveScanner(scanner: Scanner): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                val entity = scannerMapper.toEntity(scanner)
                scannerDao.insert(entity)
                logger.info(TAG, "Scanner saved: ${scanner.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error saving scanner", e)
                Result.failure(e)
            }
        }

    /**
     * Removes a scanner from the database.
     * 
     * @param scannerId Scanner ID to remove
     * @return Result indicating success or failure
     */
    override suspend fun removeScanner(scannerId: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                scannerDao.deleteById(scannerId)
                logger.info(TAG, "Scanner removed: $scannerId")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error removing scanner", e)
                Result.failure(e)
            }
        }

    /**
     * Updates a scanner in the database.
     * 
     * @param scanner Scanner to update
     * @return Result indicating success or failure
     */
    override suspend fun updateScanner(scanner: Scanner): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                val entity = scannerMapper.toEntity(scanner)
                scannerDao.update(entity)
                logger.info(TAG, "Scanner updated: ${scanner.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error updating scanner", e)
                Result.failure(e)
            }
        }

    /**
     * Updates scanner's last connected timestamp.
     */
    private suspend fun updateScannerLastConnected(scanner: Scanner) {
        try {
            val updatedScanner = scanner.copy(
                lastConnected = System.currentTimeMillis(),
                connectionCount = scanner.connectionCount + 1
            )
            updateScanner(updatedScanner)
        } catch (e: Exception) {
            logger.warn(TAG, "Failed to update last connected: ${e.message}")
        }
    }

    /**
     * Gets the last connected scanner.
     * 
     * @return Result containing the last connected scanner or null
     */
    override suspend fun getLastConnectedScanner(): Result<Scanner?> = 
        withContext(ioDispatcher) {
            try {
                val entity = scannerDao.getLastConnectedScanner()
                val scanner = entity?.let { scannerMapper.toDomain(it) }
                Result.success(scanner)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting last connected scanner", e)
                Result.failure(e)
            }
        }

    /**
     * Sets the default scanner.
     * 
     * @param scannerId Scanner ID to set as default
     * @return Result indicating success or failure
     */
    override suspend fun setDefaultScanner(scannerId: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                // Clear existing default
                scannerDao.clearDefaultScanner()
                
                // Set new default
                scannerDao.setDefaultScanner(scannerId)
                
                logger.info(TAG, "Default scanner set: $scannerId")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error setting default scanner", e)
                Result.failure(e)
            }
        }

    /**
     * Gets the default scanner.
     * 
     * @return Result containing the default scanner or null
     */
    override suspend fun getDefaultScanner(): Result<Scanner?> = 
        withContext(ioDispatcher) {
            try {
                val entity = scannerDao.getDefaultScanner()
                val scanner = entity?.let { scannerMapper.toDomain(it) }
                Result.success(scanner)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting default scanner", e)
                Result.failure(e)
            }
        }

    // ==================== Scanner Info ====================

    /**
     * Gets detailed scanner information.
     * 
     * @param scannerId Scanner ID
     * @return Result containing scanner info
     */
    override suspend fun getScannerInfo(scannerId: String): Result<ScannerInfo> = 
        withContext(ioDispatcher) {
            try {
                if (!isConnected || _connectedScanner.value?.id != scannerId) {
                    return@withContext Result.failure(
                        ConnectionException("Scanner not connected")
                    )
                }
                
                val info = readScannerInfo()
                Result.success(info)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error getting scanner info", e)
                Result.failure(e)
            }
        }

    /**
     * Reads scanner information from device.
     */
    private suspend fun readScannerInfo(): ScannerInfo {
        val scanner = _connectedScanner.value ?: throw IllegalStateException("No connected scanner available")
        
        // Read device info
        val deviceId = sendCommandInternal("ATI", 500)
        val firmwareVersion = sendCommandInternal("AT@1", 500)
        val voltage = sendCommandInternal("ATRV", 500)
        val protocol = sendCommandInternal("ATDPN", 500)
        
        return ScannerInfo(
            id = scanner.id,
            name = scanner.name,
            type = scanner.type,
            firmwareVersion = parseDeviceResponse(firmwareVersion),
            hardwareVersion = parseDeviceResponse(deviceId),
            protocolVersion = parseProtocol(protocol),
            serialNumber = null,
            capabilities = getScannerCapabilitiesInternal()
        )
    }

    /**
     * Gets scanner capabilities.
     * 
     * @return Result containing scanner capabilities
     */
    override suspend fun getScannerCapabilities(): Result<ScannerCapabilities> = 
        withContext(ioDispatcher) {
            try {
                if (!isConnected) {
                    return@withContext Result.failure(
                        ConnectionException("Scanner not connected")
                    )
                }
                
                val capabilities = getScannerCapabilitiesInternal()
                Result.success(capabilities)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error getting capabilities", e)
                Result.failure(e)
            }
        }

    /**
     * Gets capabilities from connected scanner.
     */
    private suspend fun getScannerCapabilitiesInternal(): ScannerCapabilities {
        // Test protocol support
        val supportedProtocols = mutableSetOf<ProtocolType>()
        
        // Check CAN support
        try {
            val canTest = sendCommandInternal("ATSP6", 300)
            if (!canTest.contains("ERROR")) {
                supportedProtocols.add(ProtocolType.ISO_15765_4_CAN_11BIT_500K)
                supportedProtocols.add(ProtocolType.ISO_15765_4_CAN_29BIT_500K)
            }
        } catch (e: Exception) { }
        
        // Default capabilities for ELM327-compatible adapters
        return ScannerCapabilities(
            supportedProtocols = supportedProtocols.ifEmpty {
                ProtocolType.values().filter { it != ProtocolType.AUTO }.toSet()
            },
            supportsCANFD = false,
            maxBaudRate = 500000,
            supportsBatteryVoltage = true,
            supportsJ2534 = false,
            supportsPassThru = false
        )
    }

    /**
     * Gets scanner firmware version.
     * 
     * @return Result containing firmware version string
     */
    override suspend fun getScannerFirmwareVersion(): Result<String> = 
        withContext(ioDispatcher) {
            try {
                if (!isConnected) {
                    return@withContext Result.failure(
                        ConnectionException("Scanner not connected")
                    )
                }
                
                val response = sendCommandInternal("ATI", 500)
                val version = parseDeviceResponse(response)
                
                Result.success(version)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error getting firmware version", e)
                Result.failure(e)
            }
        }

    /**
     * Checks for firmware updates.
     * 
     * @return Result containing firmware update info or null if up to date
     */
    override suspend fun checkForFirmwareUpdate(): Result<FirmwareUpdateInfo?> = 
        withContext(ioDispatcher) {
            try {
                // For ELM327 clones, firmware updates are not typically available
                // This would be implemented for custom adapter hardware
                Result.success(null)
            } catch (e: Exception) {
                logger.error(TAG, "Error checking firmware update", e)
                Result.failure(e)
            }
        }

    // ==================== Communication ====================

    /**
     * Sends a command to the scanner.
     * 
     * @param command AT command or OBD-II command
     * @return Result containing the response string
     */
    override suspend fun sendCommand(command: String): Result<String> = 
        withContext(ioDispatcher) {
            try {
                if (!isConnected) {
                    return@withContext Result.failure(
                        ConnectionException("Scanner not connected")
                    )
                }
                
                val response = sendCommandInternal(command, COMMAND_TIMEOUT_MS)
                Result.success(response)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error sending command: $command", e)
                Result.failure(CommunicationException("Command failed: ${e.message}"))
            }
        }

    /**
     * Internal command sending.
     */
    private suspend fun sendCommandInternal(command: String, timeoutMs: Long): String {
        val connection = activeConnection 
            ?: throw ConnectionException("No active connection")
        
        // Send command
        connection.write("$command\r".toByteArray())
        
        // Read response with timeout
        val response = StringBuilder()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val data = connection.read(100)
            if (data.isNotEmpty()) {
                val text = String(data, Charsets.US_ASCII)
                response.append(text)
                
                // Check for prompt indicating end of response
                if (response.contains(">")) {
                    break
                }
            }
            delay(10)
        }
        
        return response.toString()
            .replace(">", "")
            .replace("\r", "")
            .replace("\n", " ")
            .trim()
    }

    /**
     * Sends raw data to the scanner.
     * 
     * @param data Raw bytes to send
     * @return Result containing raw response bytes
     */
    override suspend fun sendRawData(data: ByteArray): Result<ByteArray> = 
        withContext(ioDispatcher) {
            try {
                if (!isConnected) {
                    return@withContext Result.failure(
                        ConnectionException("Scanner not connected")
                    )
                }
                
                val connection = activeConnection ?: return@withContext Result.failure(
                    ConnectionException("Active connection not available")
                )
                connection.write(data)
                
                val response = connection.read(COMMAND_TIMEOUT_MS)
                Result.success(response)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error sending raw data", e)
                Result.failure(CommunicationException("Raw send failed: ${e.message}"))
            }
        }

    /**
     * Observes incoming data from the scanner.
     * 
     * @return Flow emitting incoming data bytes
     */
    override fun observeIncomingData(): Flow<ByteArray> =
        _incomingData.asSharedFlow()

    // ==================== Health Monitoring ====================

    /**
     * Observes signal strength.
     * 
     * @return Flow emitting signal strength (RSSI) values
     */
    override fun observeSignalStrength(): Flow<Int> =
        _signalStrength.asStateFlow()

    /**
     * Observes battery voltage.
     * 
     * @return Flow emitting voltage values
     */
    override fun observeVoltage(): Flow<Float> =
        _voltage.asStateFlow()

    /**
     * Pings the scanner to check connection.
     * 
     * @return Result containing ping time in milliseconds
     */
    override suspend fun ping(): Result<Long> = withContext(ioDispatcher) {
        try {
            if (!isConnected) {
                return@withContext Result.failure(
                    ConnectionException("Scanner not connected")
                )
            }
            
            val startTime = System.currentTimeMillis()
            
            val response = sendCommandInternal("AT", 1000)
            
            if (response.contains("OK") || response.isNotEmpty()) {
                val pingTime = System.currentTimeMillis() - startTime
                Result.success(pingTime)
            } else {
                Result.failure(CommunicationException("Ping failed: No response"))
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Ping failed", e)
            Result.failure(e)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Parses device response to extract useful info.
     */
    private fun parseDeviceResponse(response: String): String {
        return response
            .replace("OK", "")
            .replace("?", "")
            .trim()
            .lines()
            .firstOrNull { it.isNotBlank() }
            ?: "Unknown"
    }

    /**
     * Parses protocol from ATDPN response.
     */
    private fun parseProtocol(response: String): String? {
        val code = response.trim().firstOrNull()
        return when (code) {
            '1' -> "SAE J1850 PWM"
            '2' -> "SAE J1850 VPW"
            '3' -> "ISO 9141-2"
            '4' -> "ISO 14230-4 (KWP 5-baud)"
            '5' -> "ISO 14230-4 (KWP Fast)"
            '6' -> "ISO 15765-4 (CAN 11/500)"
            '7' -> "ISO 15765-4 (CAN 29/500)"
            '8' -> "ISO 15765-4 (CAN 11/250)"
            '9' -> "ISO 15765-4 (CAN 29/250)"
            'A' -> "SAE J1939 (CAN 29/250)"
            else -> null
        }
    }

    /**
     * Parses voltage from ATRV response.
     */
    private fun parseVoltage(response: String): Float? {
        return try {
            response
                .replace("V", "")
                .replace("v", "")
                .trim()
                .toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // ==================== Companion Object ====================

    companion object {
        private const val TAG = "ScannerRepository"
        
        /** Discovery timeout in milliseconds */
        private const val DISCOVERY_TIMEOUT_MS = 30000L
        
        /** Connection timeout in milliseconds */
        private const val CONNECTION_TIMEOUT_MS = 15000L
        
        /** Command timeout in milliseconds */
        private const val COMMAND_TIMEOUT_MS = 5000L
        
        /** Health check interval in milliseconds */
        private const val HEALTH_CHECK_INTERVAL_MS = 10000L
        
        /** Default WiFi port for OBD adapters */
        private const val DEFAULT_WIFI_PORT = 35000
    }
}

// ==================== Discovery Result ====================

/**
 * Result of scanner discovery operations.
 */
sealed class DiscoveryResult {
    
    /** Discovery has started */
    object Started : DiscoveryResult() {
        override fun toString() = "Started"
    }
    
    /**
     * A scanner was found.
     * 
     * @property scanner The discovered scanner
     */
    data class Found(val scanner: Scanner) : DiscoveryResult()
    
    /**
     * A scanner was updated (e.g., signal strength changed).
     * 
     * @property scanner The updated scanner
     */
    data class Updated(val scanner: Scanner) : DiscoveryResult()
    
    /**
     * A scanner was lost (no longer visible).
     * 
     * @property scannerId ID of the lost scanner
     */
    data class Lost(val scannerId: String) : DiscoveryResult()
    
    /** Discovery completed successfully */
    object Completed : DiscoveryResult() {
        override fun toString() = "Completed"
    }
    
    /**
     * Discovery error occurred.
     * 
     * @property error The error that occurred
     */
    data class Error(val error: Throwable) : DiscoveryResult()
}

// ==================== Scanner Info ====================

/**
 * Detailed scanner information.
 * 
 * @property id Unique scanner identifier
 * @property name Scanner name
 * @property type Scanner connection type
 * @property firmwareVersion Firmware version string
 * @property hardwareVersion Hardware version string
 * @property protocolVersion Protocol version string
 * @property serialNumber Serial number (if available)
 * @property capabilities Scanner capabilities
 */
data class ScannerInfo(
    val id: String,
    val name: String,
    val type: ScannerType,
    val firmwareVersion: String,
    val hardwareVersion: String?,
    val protocolVersion: String?,
    val serialNumber: String?,
    val capabilities: ScannerCapabilities
) {
    /**
     * Full version string.
     */
    val fullVersion: String
        get() = listOfNotNull(
            hardwareVersion?.let { "HW: $it" },
            "FW: $firmwareVersion"
        ).joinToString(", ")
    
    /**
     * Display description.
     */
    val description: String
        get() = "$name (${type.displayName})"
}

// ==================== Scanner Capabilities ====================

/**
 * Scanner capabilities and supported features.
 * 
 * @property supportedProtocols Set of supported OBD-II protocols
 * @property supportsCANFD Whether CAN FD is supported
 * @property maxBaudRate Maximum supported baud rate
 * @property supportsBatteryVoltage Whether battery voltage reading is supported
 * @property supportsJ2534 Whether J2534 pass-thru is supported
 * @property supportsPassThru Whether generic pass-thru is supported
 */
data class ScannerCapabilities(
    val supportedProtocols: Set<ProtocolType>,
    val supportsCANFD: Boolean = false,
    val maxBaudRate: Int = 500000,
    val supportsBatteryVoltage: Boolean = true,
    val supportsJ2534: Boolean = false,
    val supportsPassThru: Boolean = false,
    val supportsHS_CAN: Boolean = true,
    val supportsMS_CAN: Boolean = false,
    val supportsSW_CAN: Boolean = false,
    val supportsFlexRay: Boolean = false,
    val supportsEthernet: Boolean = false,
    val maxDataRate: Int = 500000,
    val bufferSize: Int = 4096
) {
    /**
     * Whether CAN protocols are supported.
     */
    val supportsCAN: Boolean
        get() = supportedProtocols.any { it.isCAN }
    
    /**
     * Whether legacy protocols are supported.
     */
    val supportsLegacy: Boolean
        get() = supportedProtocols.any { it in ProtocolType.LEGACY_PROTOCOLS }
    
    /**
     * Number of supported protocols.
     */
    val protocolCount: Int
        get() = supportedProtocols.size
    
    /**
     * Checks if a specific protocol is supported.
     */
    fun supportsProtocol(protocol: ProtocolType): Boolean =
        protocol in supportedProtocols
    
    companion object {
        /**
         * Default capabilities for ELM327-compatible adapters.
         */
        val ELM327_DEFAULT = ScannerCapabilities(
            supportedProtocols = setOf(
                ProtocolType.ISO_15765_4_CAN_11BIT_500K,
                ProtocolType.ISO_15765_4_CAN_29BIT_500K,
                ProtocolType.ISO_15765_4_CAN_11BIT_250K,
                ProtocolType.ISO_15765_4_CAN_29BIT_250K,
                ProtocolType.ISO_14230_4_KWP_FAST,
                ProtocolType.ISO_14230_4_KWP_SLOW,
                ProtocolType.ISO_9141_2,
                ProtocolType.SAE_J1850_VPW,
                ProtocolType.SAE_J1850_PWM
            ),
            supportsCANFD = false,
            maxBaudRate = 500000,
            supportsBatteryVoltage = true
        )
        
        /**
         * Capabilities for advanced J2534-compatible adapters.
         */
        val J2534_FULL = ScannerCapabilities(
            supportedProtocols = ProtocolType.values().filter { 
                it != ProtocolType.AUTO 
            }.toSet(),
            supportsCANFD = true,
            maxBaudRate = 1000000,
            supportsBatteryVoltage = true,
            supportsJ2534 = true,
            supportsPassThru = true,
            supportsHS_CAN = true,
            supportsMS_CAN = true,
            supportsSW_CAN = true
        )
    }
}

// ==================== Firmware Update Info ====================

/**
 * Information about available firmware updates.
 * 
 * @property currentVersion Current firmware version
 * @property availableVersion Available update version
 * @property releaseNotes Release notes for the update
 * @property downloadUrl URL to download the update
 * @property mandatory Whether the update is mandatory
 * @property size Update file size in bytes
 * @property releaseDate Release date timestamp
 */
data class FirmwareUpdateInfo(
    val currentVersion: String,
    val availableVersion: String,
    val releaseNotes: String?,
    val downloadUrl: String?,
    val mandatory: Boolean = false,
    val size: Long? = null,
    val releaseDate: Long? = null
) {
    /**
     * Whether an update is available.
     */
    val updateAvailable: Boolean
        get() = currentVersion != availableVersion
    
    /**
     * Size formatted as human-readable string.
     */
    val sizeFormatted: String?
        get() = size?.let {
            when {
                it < 1024 -> "$it B"
                it < 1024 * 1024 -> "${it / 1024} KB"
                else -> "${it / (1024 * 1024)} MB"
            }
        }
}

// ==================== Connection State Extensions ====================

/**
 * Connection state for scanner connections.
 */
sealed class ConnectionState {
    /** Not connected to any scanner */
    object Disconnected : ConnectionState() {
        override fun toString() = "Disconnected"
    }
    
    /** Currently connecting to a scanner */
    object Connecting : ConnectionState() {
        override fun toString() = "Connecting"
    }
    
    /** Connected and ready */
    object Connected : ConnectionState() {
        override fun toString() = "Connected"
    }
    
    /** Attempting to reconnect after disconnection */
    object Reconnecting : ConnectionState() {
        override fun toString() = "Reconnecting"
    }
    
    /**
     * Connection error occurred.
     * 
     * @property message Error message
     */
    data class Error(val message: String) : ConnectionState() {
        override fun toString() = "Error: $message"
    }
    
    /** Whether the state represents a connected condition */
    val isConnected: Boolean
        get() = this == Connected
    
    /** Whether the state represents an active connection attempt */
    val isConnecting: Boolean
        get() = this == Connecting || this == Reconnecting
    
    /** Whether the state represents a disconnected condition */
    val isDisconnected: Boolean
        get() = this == Disconnected || this is Error
}

// ==================== Scanner Extensions ====================

/**
 * Filters scanners by type.
 */
fun List<Scanner>.filterByType(type: ScannerType): List<Scanner> =
    filter { it.type == type }

/**
 * Gets Bluetooth scanners.
 */
fun List<Scanner>.bluetooth(): List<Scanner> =
    filter { it.type == ScannerType.BLUETOOTH || it.type == ScannerType.BLUETOOTH_LE }

/**
 * Gets WiFi scanners.
 */
fun List<Scanner>.wifi(): List<Scanner> =
    filter { it.type == ScannerType.WIFI }

/**
 * Gets USB scanners.
 */
fun List<Scanner>.usb(): List<Scanner> =
    filter { it.type == ScannerType.USB }

/**
 * Sorts scanners by signal strength (strongest first).
 */
fun List<Scanner>.sortedBySignal(): List<Scanner> =
    sortedByDescending { it.signalStrength ?: Int.MIN_VALUE }

/**
 * Sorts scanners by last connection time (most recent first).
 */
fun List<Scanner>.sortedByLastConnected(): List<Scanner> =
    sortedByDescending { it.lastConnected ?: 0 }

/**
 * Gets paired/saved scanners.
 */
fun List<Scanner>.paired(): List<Scanner> =
    filter { it.isPaired }

// ==================== Exception Classes ====================

/**
 * Exception for connection-related errors.
 */
class ConnectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)