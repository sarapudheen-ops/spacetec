/**
 * File: DiagnosticRepositoryImpl.kt
 * 
 * Implementation of the DiagnosticRepository interface providing comprehensive
 * diagnostic operations for the SpaceTec automotive diagnostic application.
 * 
 * This repository coordinates between the protocol layer (for vehicle communication)
 * and the data persistence layer (for local storage and caching), implementing
 * all diagnostic operations including DTC management, freeze frame handling,
 * ECU communication, and session management.
 * 
 * Structure:
 * 1. Package declaration and imports
 * 2. Main DiagnosticRepositoryImpl class with full implementation
 * 3. Supporting data classes (AllDTCsResult, DTCOccurrence, ReportFormat)
 * 4. Extension functions for data transformation
 * 5. Internal helper classes
 * 
 * @author SpaceTec Development Team
 * @since 1.0.0
 */
package com.spacetec.data.repository

import com.spacetec.core.common.constants.OBDConstants
import com.spacetec.core.common.constants.ProtocolConstants
import com.spacetec.core.common.exceptions.CommunicationException
import com.spacetec.core.common.exceptions.ProtocolException
import com.spacetec.core.common.exceptions.TimeoutException
import com.spacetec.core.common.result.Result
import com.spacetec.core.database.dao.DiagnosticSessionDao
import com.spacetec.core.database.dao.DTCDao
import com.spacetec.core.database.dao.ECUDao
import com.spacetec.core.database.entities.DiagnosticSessionEntity
import com.spacetec.core.database.entities.DTCEntity
import com.spacetec.core.database.entities.ECUEntity
import com.spacetec.core.logging.DiagnosticLogger
import com.spacetec.data.datasource.device.OBDDataSource
import com.spacetec.data.datasource.local.DiagnosticLocalDataSource
import com.spacetec.data.datasource.remote.DTCRemoteDataSource
import com.spacetec.data.mapper.DTCMapper
import com.spacetec.data.mapper.ECUMapper
import com.spacetec.data.mapper.SessionMapper
import com.spacetec.domain.models.diagnostic.DiagnosticReport
import com.spacetec.domain.models.diagnostic.DiagnosticSession
import com.spacetec.domain.models.diagnostic.DiagnosticSessionType
import com.spacetec.domain.models.diagnostic.DTC
import com.spacetec.domain.models.diagnostic.DTCStatus
import com.spacetec.domain.models.diagnostic.DTCType
import com.spacetec.domain.models.diagnostic.FreezeFrame
import com.spacetec.domain.models.diagnostic.SessionStatus
import com.spacetec.domain.models.ecu.ECU
import com.spacetec.domain.repository.DiagnosticRepository
import com.spacetec.protocol.core.ProtocolManager
import com.spacetec.protocol.core.ProtocolState
import com.spacetec.protocol.core.ProtocolType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [DiagnosticRepository] that provides comprehensive diagnostic
 * operations for vehicle diagnostics.
 * 
 * This class serves as the single source of truth for all diagnostic data,
 * coordinating between:
 * - [ProtocolManager]: For communication with the vehicle's ECUs
 * - [DiagnosticLocalDataSource]: For local caching and offline access
 * - [DTCRemoteDataSource]: For DTC descriptions and database sync
 * - Room DAOs: For persistent storage of sessions, DTCs, and ECU data
 * 
 * ## Thread Safety
 * All operations are thread-safe through the use of:
 * - Coroutine dispatchers for appropriate thread context
 * - Mutex for state synchronization
 * - Immutable data structures
 * 
 * ## Error Handling
 * All operations return [Result] wrapper to handle success/failure cases.
 * Errors are logged and propagated with meaningful messages.
 * 
 * ## Caching Strategy
 * - DTCs are cached locally after each read
 * - Session data is persisted immediately
 * - DTC descriptions are cached from remote database
 * 
 * @property protocolManager Manages communication protocols with the vehicle
 * @property diagnosticLocalDataSource Local data source for caching
 * @property dtcRemoteDataSource Remote data source for DTC descriptions
 * @property diagnosticSessionDao DAO for session persistence
 * @property dtcDao DAO for DTC persistence
 * @property ecuDao DAO for ECU persistence
 * @property dtcMapper Mapper for DTC entity transformations
 * @property sessionMapper Mapper for session entity transformations
 * @property ecuMapper Mapper for ECU entity transformations
 * @property ioDispatcher Dispatcher for I/O operations
 * 
 * @see DiagnosticRepository
 * @see ProtocolManager
 * @see DiagnosticSession
 */
@Singleton
class DiagnosticRepositoryImpl @Inject constructor(
    private val protocolManager: ProtocolManager,
    private val diagnosticLocalDataSource: DiagnosticLocalDataSource,
    private val dtcRemoteDataSource: DTCRemoteDataSource,
    private val diagnosticSessionDao: DiagnosticSessionDao,
    private val dtcDao: DTCDao,
    private val ecuDao: ECUDao,
    private val dtcMapper: DTCMapper,
    private val sessionMapper: SessionMapper,
    private val ecuMapper: ECUMapper,
    private val logger: DiagnosticLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DiagnosticRepository {

    // ==================== Private Properties ====================

    /**
     * Coroutine scope for background operations.
     */
    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    /**
     * Mutex for synchronizing session state changes.
     */
    private val sessionMutex = Mutex()

    /**
     * Mutex for synchronizing DTC operations.
     */
    private val dtcMutex = Mutex()

    /**
     * Current active diagnostic session.
     */
    private val _currentSession = MutableStateFlow<DiagnosticSession?>(null)

    /**
     * Cache of DTCs from the current session.
     */
    private val _cachedDTCs = MutableStateFlow<List<DTC>>(emptyList())

    /**
     * Cache of discovered ECUs.
     */
    private val _cachedECUs = MutableStateFlow<List<ECU>>(emptyList())

    /**
     * Date formatter for report generation.
     */
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // ==================== Initialization ====================

    init {
        // Initialize caches from local storage
        repositoryScope.launch {
            initializeCaches()
        }
    }

    /**
     * Initializes caches from local storage on startup.
     */
    private suspend fun initializeCaches() {
        try {
            // Load last session if it was not completed
            val lastSession = diagnosticSessionDao.getLastSession()
            if (lastSession != null && lastSession.status == SessionStatus.IN_PROGRESS.name) {
                _currentSession.value = sessionMapper.toDomain(lastSession)
                logger.debug(TAG, "Restored incomplete session: ${lastSession.id}")
            }
        } catch (e: Exception) {
            logger.error(TAG, "Failed to initialize caches", e)
        }
    }

    // ==================== DTC Operations ====================

    /**
     * Reads DTCs of the specified type from the connected vehicle.
     * 
     * This method communicates with the vehicle's ECU(s) to retrieve
     * diagnostic trouble codes. The operation is performed through
     * the [ProtocolManager] using the appropriate OBD-II service.
     * 
     * ## DTC Types
     * - [DTCType.STORED]: Confirmed/stored DTCs (Service 03)
     * - [DTCType.PENDING]: Pending/maturing DTCs (Service 07)
     * - [DTCType.PERMANENT]: Permanent DTCs (Service 0A)
     * 
     * @param type The type of DTCs to read
     * @return [Result] containing list of DTCs or error
     * 
     * @throws CommunicationException If communication with vehicle fails
     * @throws ProtocolException If protocol error occurs
     * @throws TimeoutException If operation times out
     */
    override suspend fun readDTCs(type: DTCType): Result<List<DTC>> = withContext(ioDispatcher) {
        dtcMutex.withLock {
            try {
                logger.info(TAG, "Reading DTCs of type: $type")
                
                // Verify protocol is ready
                if (!protocolManager.isReady) {
                    return@withContext Result.failure(
                        CommunicationException("Protocol not ready. Please connect to vehicle first.")
                    )
                }

                // Get the appropriate service for DTC type
                val service = when (type) {
                    DTCType.STORED -> OBDConstants.SERVICE_READ_DTC
                    DTCType.PENDING -> OBDConstants.SERVICE_READ_PENDING_DTC
                    DTCType.PERMANENT -> OBDConstants.SERVICE_READ_PERMANENT_DTC
                    DTCType.ALL -> return@withContext readAllDTCs().map { it.allDTCs }
                }

                // Request DTCs from vehicle
                val response = protocolManager.sendService(service)
                
                // Parse DTC response
                val dtcs = parseDTCResponse(response, type)
                
                // Enrich DTCs with descriptions
                val enrichedDTCs = enrichDTCsWithDescriptions(dtcs)
                
                // Update cache
                updateDTCCache(enrichedDTCs, type)
                
                // Save to database
                val sessionId = _currentSession.value?.id
                if (sessionId != null) {
                    saveDTCsToDatabase(enrichedDTCs, sessionId)
                }

                logger.info(TAG, "Successfully read ${enrichedDTCs.size} ${type.name} DTCs")
                Result.success(enrichedDTCs)
                
            } catch (e: TimeoutException) {
                logger.error(TAG, "Timeout reading DTCs", e)
                Result.failure(TimeoutException("Timeout reading DTCs: ${e.message}"))
            } catch (e: CommunicationException) {
                logger.error(TAG, "Communication error reading DTCs", e)
                Result.failure(e)
            } catch (e: Exception) {
                logger.error(TAG, "Unexpected error reading DTCs", e)
                Result.failure(ProtocolException("Failed to read DTCs: ${e.message}"))
            }
        }
    }

    /**
     * Reads all types of DTCs (stored, pending, permanent) from the vehicle.
     * 
     * This method performs multiple requests to retrieve all DTC types
     * in a single operation, providing a comprehensive view of the
     * vehicle's diagnostic state.
     * 
     * @return [Result] containing [AllDTCsResult] with categorized DTCs
     */
    override suspend fun readAllDTCs(): Result<AllDTCsResult> = withContext(ioDispatcher) {
        try {
            logger.info(TAG, "Reading all DTCs from vehicle")
            
            if (!protocolManager.isReady) {
                return@withContext Result.failure(
                    CommunicationException("Protocol not ready. Please connect to vehicle first.")
                )
            }

            // Read each type of DTC
            val storedResult = readDTCsInternal(DTCType.STORED)
            val pendingResult = readDTCsInternal(DTCType.PENDING)
            val permanentResult = readDTCsInternal(DTCType.PERMANENT)

            // Combine results
            val stored = storedResult.getOrElse { emptyList() }
            val pending = pendingResult.getOrElse { emptyList() }
            val permanent = permanentResult.getOrElse { emptyList() }

            val result = AllDTCsResult(
                stored = stored,
                pending = pending,
                permanent = permanent,
                timestamp = System.currentTimeMillis()
            )

            // Update cache with all DTCs
            _cachedDTCs.value = result.allDTCs

            logger.info(TAG, "Read all DTCs: ${stored.size} stored, ${pending.size} pending, ${permanent.size} permanent")
            Result.success(result)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error reading all DTCs", e)
            Result.failure(ProtocolException("Failed to read all DTCs: ${e.message}"))
        }
    }

    /**
     * Internal method to read DTCs without mutex lock.
     */
    private suspend fun readDTCsInternal(type: DTCType): Result<List<DTC>> {
        return try {
            val service = when (type) {
                DTCType.STORED -> OBDConstants.SERVICE_READ_DTC
                DTCType.PENDING -> OBDConstants.SERVICE_READ_PENDING_DTC
                DTCType.PERMANENT -> OBDConstants.SERVICE_READ_PERMANENT_DTC
                DTCType.ALL -> throw IllegalArgumentException("Use readAllDTCs for ALL type")
            }

            val response = protocolManager.sendService(service)
            val dtcs = parseDTCResponse(response, type)
            val enrichedDTCs = enrichDTCsWithDescriptions(dtcs)
            
            Result.success(enrichedDTCs)
        } catch (e: Exception) {
            logger.warn(TAG, "Failed to read ${type.name} DTCs: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Clears all diagnostic trouble codes from the vehicle.
     * 
     * This operation sends the OBD-II Service 04 command to clear
     * all stored, pending, and permanent DTCs. It also resets the
     * MIL (Malfunction Indicator Lamp) and freeze frame data.
     * 
     * **Warning:** This operation cannot be undone. The vehicle must
     * be in a specific state (engine running, not moving) for some
     * vehicles to accept the clear command.
     * 
     * @return [Result] indicating success or failure
     */
    override suspend fun clearDTCs(): Result<Boolean> = withContext(ioDispatcher) {
        dtcMutex.withLock {
            try {
                logger.info(TAG, "Clearing all DTCs")
                
                if (!protocolManager.isReady) {
                    return@withContext Result.failure(
                        CommunicationException("Protocol not ready. Please connect to vehicle first.")
                    )
                }

                // Send clear DTCs command
                val response = protocolManager.sendService(OBDConstants.SERVICE_CLEAR_DTC)
                
                // Verify response indicates success
                val success = validateClearDTCResponse(response)
                
                if (success) {
                    // Clear local cache
                    _cachedDTCs.value = emptyList()
                    
                    // Update current session
                    _currentSession.value?.let { session ->
                        val updatedSession = session.copy(
                            dtcsCleared = true,
                            dtcsClearedAt = System.currentTimeMillis(),
                            dtcs = emptyList()
                        )
                        _currentSession.value = updatedSession
                        
                        // Persist session update
                        diagnosticSessionDao.update(sessionMapper.toEntity(updatedSession))
                    }
                    
                    logger.info(TAG, "Successfully cleared all DTCs")
                } else {
                    logger.warn(TAG, "Clear DTCs command may not have completed successfully")
                }
                
                Result.success(success)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error clearing DTCs", e)
                Result.failure(ProtocolException("Failed to clear DTCs: ${e.message}"))
            }
        }
    }

    /**
     * Gets the count of DTCs of the specified type.
     * 
     * This is a lightweight operation that only retrieves the count
     * without reading the full DTC data. Uses OBD-II Service 01 PID 01.
     * 
     * @param type The type of DTCs to count
     * @return [Result] containing the DTC count
     */
    override suspend fun getDTCCount(type: DTCType): Result<Int> = withContext(ioDispatcher) {
        try {
            logger.debug(TAG, "Getting DTC count for type: $type")
            
            if (!protocolManager.isReady) {
                return@withContext Result.failure(
                    CommunicationException("Protocol not ready")
                )
            }

            // Use Mode 01 PID 01 for stored DTC count
            if (type == DTCType.STORED || type == DTCType.ALL) {
                val response = protocolManager.sendPID(0x01, 0x01)
                val count = parseDTCCountFromPID01(response)
                return@withContext Result.success(count)
            }
            
            // For other types, we need to read the DTCs
            val dtcs = readDTCsInternal(type).getOrElse { emptyList() }
            Result.success(dtcs.size)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error getting DTC count", e)
            Result.failure(e)
        }
    }

    /**
     * Gets detailed information for a specific DTC code.
     * 
     * This method first checks local cache, then queries the remote
     * DTC database for descriptions and additional information.
     * 
     * @param code The DTC code (e.g., "P0300")
     * @return [Result] containing the DTC details or null if not found
     */
    override suspend fun getDTCDetails(code: String): Result<DTC?> = withContext(ioDispatcher) {
        try {
            logger.debug(TAG, "Getting details for DTC: $code")
            
            // Check local cache first
            val cachedDTC = _cachedDTCs.value.find { it.code == code }
            if (cachedDTC != null && cachedDTC.description != null) {
                return@withContext Result.success(cachedDTC)
            }
            
            // Check database
            val dbDTC = dtcDao.getDTCByCode(code)
            if (dbDTC != null) {
                return@withContext Result.success(dtcMapper.toDomain(dbDTC))
            }
            
            // Query remote database
            val description = dtcRemoteDataSource.getDTCDescription(code)
            
            if (description != null) {
                val dtc = DTC(
                    code = code,
                    description = description.description,
                    system = parseDTCSystem(code),
                    severity = description.severity,
                    possibleCauses = description.possibleCauses,
                    suggestedActions = description.suggestedActions
                )
                
                // Cache the result
                dtcDao.insert(dtcMapper.toEntity(dtc))
                
                Result.success(dtc)
            } else {
                // Create basic DTC with just the code
                Result.success(DTC(code = code, system = parseDTCSystem(code)))
            }
            
        } catch (e: Exception) {
            logger.error(TAG, "Error getting DTC details", e)
            Result.failure(e)
        }
    }

    /**
     * Observes DTCs as a reactive stream.
     * 
     * @return [Flow] emitting list of DTCs
     */
    override fun observeDTCs(): Flow<List<DTC>> {
        return _cachedDTCs.asStateFlow()
    }

    /**
     * Observes DTCs for a specific vehicle.
     * 
     * @param vehicleId The vehicle identifier
     * @return [Flow] emitting list of DTCs for the vehicle
     */
    override fun observeDTCsByVehicle(vehicleId: String): Flow<List<DTC>> {
        return dtcDao.observeDTCsByVehicle(vehicleId)
            .map { entities -> entities.map { dtcMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
            .catch { e ->
                logger.error(TAG, "Error observing DTCs for vehicle: $vehicleId", e)
                emit(emptyList())
            }
    }

    /**
     * Saves DTCs to the database.
     * 
     * @param dtcs List of DTCs to save
     * @param sessionId Session ID to associate with
     * @return [Result] indicating success or failure
     */
    override suspend fun saveDTCs(dtcs: List<DTC>, sessionId: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                saveDTCsToDatabase(dtcs, sessionId)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error saving DTCs", e)
                Result.failure(e)
            }
        }

    /**
     * Gets the history of a specific DTC code.
     * 
     * @param code The DTC code
     * @return [Result] containing list of DTC occurrences
     */
    override suspend fun getDTCHistory(code: String): Result<List<DTCOccurrence>> = 
        withContext(ioDispatcher) {
            try {
                val occurrences = dtcDao.getDTCHistory(code)
                    .map { entity ->
                        DTCOccurrence(
                            dtc = dtcMapper.toDomain(entity),
                            sessionId = entity.sessionId,
                            timestamp = entity.timestamp,
                            mileage = entity.mileage,
                            wasCleared = entity.wasCleared
                        )
                    }
                Result.success(occurrences)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting DTC history", e)
                Result.failure(e)
            }
        }

    // ==================== Freeze Frame Operations ====================

    /**
     * Reads freeze frame data for a specific DTC.
     * 
     * Freeze frame data captures the vehicle's sensor values at the
     * moment a DTC was set, providing valuable diagnostic context.
     * 
     * @param dtc The DTC to read freeze frame for
     * @return [Result] containing freeze frame data or null if none
     */
    override suspend fun readFreezeFrame(dtc: DTC): Result<FreezeFrame?> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Reading freeze frame for DTC: ${dtc.code}")
                
                if (!protocolManager.isReady) {
                    return@withContext Result.failure(
                        CommunicationException("Protocol not ready")
                    )
                }

                // Get frame number from DTC
                val frameNumber = dtc.frameNumber ?: 0
                
                // Read supported freeze frame PIDs
                val supportedPIDs = readSupportedFreezeFramePIDs(frameNumber)
                
                if (supportedPIDs.isEmpty()) {
                    logger.debug(TAG, "No freeze frame data available for DTC: ${dtc.code}")
                    return@withContext Result.success(null)
                }
                
                // Read each PID value
                val pidValues = mutableMapOf<Int, Any>()
                for (pid in supportedPIDs) {
                    try {
                        val value = protocolManager.sendService(
                            OBDConstants.SERVICE_READ_FREEZE_FRAME,
                            byteArrayOf(pid.toByte(), frameNumber.toByte())
                        )
                        pidValues[pid] = parsePIDValue(pid, value)
                    } catch (e: Exception) {
                        logger.warn(TAG, "Failed to read freeze frame PID 0x${pid.toString(16)}")
                    }
                }
                
                val freezeFrame = FreezeFrame(
                    dtcCode = dtc.code,
                    frameNumber = frameNumber,
                    timestamp = System.currentTimeMillis(),
                    pidValues = pidValues
                )
                
                // Save to database
                saveFreezeFrameToDatabase(freezeFrame)
                
                Result.success(freezeFrame)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error reading freeze frame", e)
                Result.failure(e)
            }
        }

    /**
     * Reads all available freeze frames from the vehicle.
     * 
     * @return [Result] containing list of freeze frames
     */
    override suspend fun readAllFreezeFrames(): Result<List<FreezeFrame>> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Reading all freeze frames")
                
                if (!protocolManager.isReady) {
                    return@withContext Result.failure(
                        CommunicationException("Protocol not ready")
                    )
                }

                // Get all DTCs with freeze frames
                val dtcs = _cachedDTCs.value.filter { it.hasFreezeFrame }
                
                val freezeFrames = mutableListOf<FreezeFrame>()
                for (dtc in dtcs) {
                    readFreezeFrame(dtc).onSuccess { ff ->
                        ff?.let { freezeFrames.add(it) }
                    }
                }
                
                Result.success(freezeFrames)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error reading all freeze frames", e)
                Result.failure(e)
            }
        }

    /**
     * Saves a freeze frame to the database.
     * 
     * @param freezeFrame The freeze frame to save
     * @return [Result] indicating success or failure
     */
    override suspend fun saveFreezeFrame(freezeFrame: FreezeFrame): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                saveFreezeFrameToDatabase(freezeFrame)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error saving freeze frame", e)
                Result.failure(e)
            }
        }

    // ==================== Session Operations ====================

    /**
     * Starts a new diagnostic session.
     * 
     * A diagnostic session tracks all operations performed during
     * a single connection to a vehicle, including DTCs read, ECUs
     * scanned, and any live data recorded.
     * 
     * @param vehicleId The vehicle identifier (VIN or internal ID)
     * @param sessionType The type of diagnostic session
     * @return [Result] containing the new session
     */
    override suspend fun startSession(
        vehicleId: String,
        sessionType: DiagnosticSessionType
    ): Result<DiagnosticSession> = withContext(ioDispatcher) {
        sessionMutex.withLock {
            try {
                logger.info(TAG, "Starting new $sessionType session for vehicle: $vehicleId")
                
                // End any existing session
                _currentSession.value?.let { existingSession ->
                    if (existingSession.status == SessionStatus.IN_PROGRESS) {
                        endSessionInternal(existingSession.id)
                    }
                }

                // Create new session
                val session = DiagnosticSession(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    type = sessionType,
                    status = SessionStatus.IN_PROGRESS,
                    startTime = System.currentTimeMillis(),
                    protocol = protocolManager.currentProtocol
                )
                
                // Save to database
                diagnosticSessionDao.insert(sessionMapper.toEntity(session))
                
                // Update current session
                _currentSession.value = session
                
                // Clear caches for new session
                _cachedDTCs.value = emptyList()
                _cachedECUs.value = emptyList()
                
                logger.info(TAG, "Session started: ${session.id}")
                Result.success(session)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error starting session", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Ends the current diagnostic session.
     * 
     * @param sessionId The session ID to end
     * @return [Result] containing the completed session
     */
    override suspend fun endSession(sessionId: String): Result<DiagnosticSession> = 
        withContext(ioDispatcher) {
            sessionMutex.withLock {
                try {
                    logger.info(TAG, "Ending session: $sessionId")
                    
                    val session = endSessionInternal(sessionId)
                    if (session != null) {
                        Result.success(session)
                    } else {
                        Result.failure(IllegalStateException("Session not found: $sessionId"))
                    }
                    
                } catch (e: Exception) {
                    logger.error(TAG, "Error ending session", e)
                    Result.failure(e)
                }
            }
        }

    /**
     * Internal method to end a session.
     */
    private suspend fun endSessionInternal(sessionId: String): DiagnosticSession? {
        val session = if (_currentSession.value?.id == sessionId) {
            _currentSession.value
        } else {
            diagnosticSessionDao.getSession(sessionId)?.let { sessionMapper.toDomain(it) }
        }
        
        if (session == null) {
            logger.warn(TAG, "Session not found: $sessionId")
            return null
        }
        
        // Complete the session
        val completedSession = session.copy(
            status = SessionStatus.COMPLETED,
            endTime = System.currentTimeMillis(),
            dtcs = _cachedDTCs.value,
            ecus = _cachedECUs.value
        )
        
        // Update database
        diagnosticSessionDao.update(sessionMapper.toEntity(completedSession))
        
        // Clear current session if it matches
        if (_currentSession.value?.id == sessionId) {
            _currentSession.value = null
        }
        
        logger.info(TAG, "Session completed: $sessionId, duration: ${completedSession.durationFormatted}")
        return completedSession
    }

    /**
     * Gets the current active session.
     * 
     * @return The current session or null if none active
     */
    override suspend fun getCurrentSession(): DiagnosticSession? {
        return _currentSession.value
    }

    /**
     * Observes the current session as a reactive stream.
     * 
     * @return [Flow] emitting the current session
     */
    override fun observeCurrentSession(): Flow<DiagnosticSession?> {
        return _currentSession.asStateFlow()
    }

    /**
     * Gets the session history for a vehicle.
     * 
     * @param vehicleId The vehicle identifier
     * @return [Result] containing list of sessions
     */
    override suspend fun getSessionHistory(vehicleId: String): Result<List<DiagnosticSession>> = 
        withContext(ioDispatcher) {
            try {
                val sessions = diagnosticSessionDao.getSessionsForVehicle(vehicleId)
                    .map { sessionMapper.toDomain(it) }
                Result.success(sessions)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting session history", e)
                Result.failure(e)
            }
        }

    /**
     * Observes session history for a vehicle.
     * 
     * @param vehicleId The vehicle identifier
     * @return [Flow] emitting list of sessions
     */
    override fun observeSessionHistory(vehicleId: String): Flow<List<DiagnosticSession>> {
        return diagnosticSessionDao.observeSessionsForVehicle(vehicleId)
            .map { entities -> entities.map { sessionMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
            .distinctUntilChanged()
    }

    /**
     * Gets a specific session by ID.
     * 
     * @param sessionId The session ID
     * @return [Result] containing the session or null
     */
    override suspend fun getSession(sessionId: String): Result<DiagnosticSession?> = 
        withContext(ioDispatcher) {
            try {
                val entity = diagnosticSessionDao.getSession(sessionId)
                val session = entity?.let { sessionMapper.toDomain(it) }
                Result.success(session)
            } catch (e: Exception) {
                logger.error(TAG, "Error getting session", e)
                Result.failure(e)
            }
        }

    /**
     * Deletes a session.
     * 
     * @param sessionId The session ID to delete
     * @return [Result] indicating success or failure
     */
    override suspend fun deleteSession(sessionId: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                diagnosticSessionDao.deleteSession(sessionId)
                dtcDao.deleteDTCsForSession(sessionId)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error deleting session", e)
                Result.failure(e)
            }
        }

    // ==================== ECU Operations ====================

    /**
     * Scans for all available ECUs on the vehicle's diagnostic bus.
     * 
     * This operation broadcasts a query to all possible ECU addresses
     * and collects responses from those that are present.
     * 
     * @return [Result] containing list of discovered ECUs
     */
    override suspend fun scanECUs(): Result<List<ECU>> = withContext(ioDispatcher) {
        try {
            logger.info(TAG, "Scanning for ECUs")
            
            if (!protocolManager.isReady) {
                return@withContext Result.failure(
                    CommunicationException("Protocol not ready")
                )
            }

            val discoveredECUs = mutableListOf<ECU>()
            
            // Scan standard OBD-II addresses
            for (address in ECU.STANDARD_ADDRESSES) {
                try {
                    val response = protocolManager.sendToAddress(
                        address,
                        OBDConstants.SERVICE_VEHICLE_INFO,
                        byteArrayOf(0x00) // Request supported PIDs
                    )
                    
                    if (response.isNotEmpty()) {
                        val ecu = parseECUResponse(address, response)
                        discoveredECUs.add(ecu)
                        logger.debug(TAG, "Found ECU at address 0x${address.toString(16)}: ${ecu.name}")
                    }
                } catch (e: TimeoutException) {
                    // No ECU at this address, continue
                    logger.debug(TAG, "No response from address 0x${address.toString(16)}")
                } catch (e: Exception) {
                    logger.warn(TAG, "Error scanning address 0x${address.toString(16)}: ${e.message}")
                }
            }
            
            // Get additional info for each ECU
            for (ecu in discoveredECUs) {
                try {
                    enrichECUInfo(ecu)
                } catch (e: Exception) {
                    logger.warn(TAG, "Error enriching ECU info for ${ecu.name}")
                }
            }
            
            // Update cache
            _cachedECUs.value = discoveredECUs
            
            // Save to session
            val sessionId = _currentSession.value?.id
            if (sessionId != null) {
                saveECUs(discoveredECUs, sessionId)
            }
            
            logger.info(TAG, "Found ${discoveredECUs.size} ECUs")
            Result.success(discoveredECUs)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error scanning ECUs", e)
            Result.failure(e)
        }
    }

    /**
     * Gets detailed information for a specific ECU.
     * 
     * @param address The ECU address
     * @return [Result] containing ECU info or null
     */
    override suspend fun getECUInfo(address: Int): Result<ECU?> = withContext(ioDispatcher) {
        try {
            logger.debug(TAG, "Getting ECU info for address 0x${address.toString(16)}")
            
            // Check cache first
            val cachedECU = _cachedECUs.value.find { it.address == address }
            if (cachedECU != null) {
                return@withContext Result.success(cachedECU)
            }
            
            if (!protocolManager.isReady) {
                return@withContext Result.failure(
                    CommunicationException("Protocol not ready")
                )
            }

            // Query ECU directly
            val response = protocolManager.sendToAddress(
                address,
                OBDConstants.SERVICE_VEHICLE_INFO,
                byteArrayOf(0x00)
            )
            
            if (response.isEmpty()) {
                return@withContext Result.success(null)
            }
            
            val ecu = parseECUResponse(address, response)
            enrichECUInfo(ecu)
            
            Result.success(ecu)
            
        } catch (e: TimeoutException) {
            Result.success(null)
        } catch (e: Exception) {
            logger.error(TAG, "Error getting ECU info", e)
            Result.failure(e)
        }
    }

    /**
     * Observes discovered ECUs.
     * 
     * @return [Flow] emitting list of ECUs
     */
    override fun observeECUs(): Flow<List<ECU>> {
        return _cachedECUs.asStateFlow()
    }

    /**
     * Saves ECUs to the database.
     * 
     * @param ecus List of ECUs to save
     * @param sessionId Session ID to associate with
     * @return [Result] indicating success or failure
     */
    override suspend fun saveECUs(ecus: List<ECU>, sessionId: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                val entities = ecus.map { ecu ->
                    ecuMapper.toEntity(ecu, sessionId)
                }
                ecuDao.insertAll(entities)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error(TAG, "Error saving ECUs", e)
                Result.failure(e)
            }
        }

    // ==================== Report Generation ====================

    /**
     * Generates a comprehensive diagnostic report for a session.
     * 
     * @param sessionId The session ID
     * @return [Result] containing the diagnostic report
     */
    override suspend fun generateReport(sessionId: String): Result<DiagnosticReport> = 
        withContext(ioDispatcher) {
            try {
                logger.info(TAG, "Generating report for session: $sessionId")
                
                val session = diagnosticSessionDao.getSession(sessionId)
                    ?.let { sessionMapper.toDomain(it) }
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Session not found: $sessionId")
                    )
                
                val dtcs = dtcDao.getDTCsForSession(sessionId)
                    .map { dtcMapper.toDomain(it) }
                
                val ecus = ecuDao.getECUsForSession(sessionId)
                    .map { ecuMapper.toDomain(it) }
                
                val report = DiagnosticReport(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    vehicleId = session.vehicleId,
                    vin = session.vin,
                    generatedAt = System.currentTimeMillis(),
                    sessionType = session.type,
                    sessionDuration = session.durationMs,
                    dtcs = dtcs,
                    ecus = ecus,
                    freezeFrames = session.freezeFrames,
                    mileage = session.mileage,
                    mileageUnit = session.mileageUnit,
                    notes = session.notes,
                    summary = generateReportSummary(session, dtcs, ecus)
                )
                
                logger.info(TAG, "Report generated successfully")
                Result.success(report)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error generating report", e)
                Result.failure(e)
            }
        }

    /**
     * Exports a diagnostic report to the specified format.
     * 
     * @param report The report to export
     * @param format The export format
     * @return [Result] containing the exported data as bytes
     */
    override suspend fun exportReport(
        report: DiagnosticReport,
        format: ReportFormat
    ): Result<ByteArray> = withContext(ioDispatcher) {
        try {
            logger.info(TAG, "Exporting report in ${format.name} format")
            
            val bytes = when (format) {
                ReportFormat.PDF -> exportToPDF(report)
                ReportFormat.HTML -> exportToHTML(report)
                ReportFormat.JSON -> exportToJSON(report)
                ReportFormat.CSV -> exportToCSV(report)
                ReportFormat.TXT -> exportToText(report)
            }
            
            Result.success(bytes)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error exporting report", e)
            Result.failure(e)
        }
    }

    // ==================== Sync Operations ====================

    /**
     * Synchronizes the local DTC database with the remote server.
     * 
     * @return [Result] containing the number of updated entries
     */
    override suspend fun syncDTCDatabase(): Result<Int> = withContext(ioDispatcher) {
        try {
            logger.info(TAG, "Syncing DTC database")
            
            val lastSyncTime = diagnosticLocalDataSource.getLastDTCSyncTime()
            val updates = dtcRemoteDataSource.getDTCUpdates(lastSyncTime)
            
            var updateCount = 0
            for (update in updates) {
                try {
                    dtcDao.insertOrUpdate(dtcMapper.toDescriptionEntity(update))
                    updateCount++
                } catch (e: Exception) {
                    logger.warn(TAG, "Failed to update DTC: ${update.code}")
                }
            }
            
            diagnosticLocalDataSource.setLastDTCSyncTime(System.currentTimeMillis())
            
            logger.info(TAG, "DTC database synced: $updateCount updates")
            Result.success(updateCount)
            
        } catch (e: Exception) {
            logger.error(TAG, "Error syncing DTC database", e)
            Result.failure(e)
        }
    }

    /**
     * Looks up description for a DTC code.
     * 
     * @param code The DTC code
     * @return [Result] containing the description or null
     */
    override suspend fun lookupDTCDescription(code: String): Result<String?> = 
        withContext(ioDispatcher) {
            try {
                // Check local database first
                val local = dtcDao.getDescriptionForCode(code)
                if (local != null) {
                    return@withContext Result.success(local)
                }
                
                // Query remote
                val remote = dtcRemoteDataSource.getDTCDescription(code)
                
                Result.success(remote?.description)
                
            } catch (e: Exception) {
                logger.error(TAG, "Error looking up DTC description", e)
                Result.failure(e)
            }
        }

    // ==================== Private Helper Methods ====================

    /**
     * Parses DTC response bytes into DTC objects.
     */
    private fun parseDTCResponse(response: ByteArray, type: DTCType): List<DTC> {
        if (response.isEmpty()) return emptyList()
        
        val dtcs = mutableListOf<DTC>()
        
        // Skip service ID in response
        val data = if (response[0] == (OBDConstants.SERVICE_READ_DTC + 0x40).toByte() ||
                       response[0] == (OBDConstants.SERVICE_READ_PENDING_DTC + 0x40).toByte() ||
                       response[0] == (OBDConstants.SERVICE_READ_PERMANENT_DTC + 0x40).toByte()) {
            response.drop(1).toByteArray()
        } else {
            response
        }
        
        // Each DTC is 2 bytes
        for (i in data.indices step 2) {
            if (i + 1 >= data.size) break
            
            val byte1 = data[i].toInt() and 0xFF
            val byte2 = data[i + 1].toInt() and 0xFF
            
            // Skip padding (0x00 0x00)
            if (byte1 == 0 && byte2 == 0) continue
            
            val code = decodeDTCBytes(byte1, byte2)
            
            dtcs.add(DTC(
                code = code,
                type = type,
                system = parseDTCSystem(code),
                timestamp = System.currentTimeMillis()
            ))
        }
        
        return dtcs
    }

    /**
     * Decodes two bytes into DTC code string.
     */
    private fun decodeDTCBytes(byte1: Int, byte2: Int): String {
        // First two bits determine system
        val systemChar = when ((byte1 shr 6) and 0x03) {
            0 -> 'P' // Powertrain
            1 -> 'C' // Chassis
            2 -> 'B' // Body
            3 -> 'U' // Network
            else -> 'P'
        }
        
        // Next two bits are first digit
        val digit1 = (byte1 shr 4) and 0x03
        
        // Last four bits of first byte + all of second byte
        val digit2 = byte1 and 0x0F
        val digit3 = (byte2 shr 4) and 0x0F
        val digit4 = byte2 and 0x0F
        
        return "$systemChar$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}"
    }

    /**
     * Parses DTC system from code prefix.
     */
    private fun parseDTCSystem(code: String): String {
        return when {
            code.startsWith("P0") || code.startsWith("P2") -> "Powertrain (Generic)"
            code.startsWith("P1") || code.startsWith("P3") -> "Powertrain (Manufacturer)"
            code.startsWith("C0") -> "Chassis (Generic)"
            code.startsWith("C1") || code.startsWith("C2") || code.startsWith("C3") -> "Chassis (Manufacturer)"
            code.startsWith("B0") -> "Body (Generic)"
            code.startsWith("B1") || code.startsWith("B2") || code.startsWith("B3") -> "Body (Manufacturer)"
            code.startsWith("U0") -> "Network (Generic)"
            code.startsWith("U1") || code.startsWith("U2") || code.startsWith("U3") -> "Network (Manufacturer)"
            else -> "Unknown"
        }
    }

    /**
     * Enriches DTCs with descriptions from database.
     */
    private suspend fun enrichDTCsWithDescriptions(dtcs: List<DTC>): List<DTC> {
        return dtcs.map { dtc ->
            val description = lookupDTCDescription(dtc.code).getOrNull()
            if (description != null) {
                dtc.copy(description = description)
            } else {
                dtc
            }
        }
    }

    /**
     * Updates the DTC cache.
     */
    private fun updateDTCCache(newDTCs: List<DTC>, type: DTCType) {
        val currentDTCs = _cachedDTCs.value.toMutableList()
        
        // Remove old DTCs of this type
        currentDTCs.removeAll { it.type == type }
        
        // Add new DTCs
        currentDTCs.addAll(newDTCs)
        
        _cachedDTCs.value = currentDTCs
    }

    /**
     * Saves DTCs to database.
     */
    private suspend fun saveDTCsToDatabase(dtcs: List<DTC>, sessionId: String) {
        val entities = dtcs.map { dtc ->
            dtcMapper.toEntity(dtc, sessionId)
        }
        dtcDao.insertAll(entities)
    }

    /**
     * Validates clear DTC response.
     */
    private fun validateClearDTCResponse(response: ByteArray): Boolean {
        if (response.isEmpty()) return false
        
        // Positive response should be 0x44 (0x04 + 0x40)
        return response[0] == 0x44.toByte()
    }

    /**
     * Parses DTC count from PID 01 response.
     */
    private fun parseDTCCountFromPID01(response: ByteArray): Int {
        if (response.size < 4) return 0
        
        // Byte B (index 2) contains DTC count in lower 7 bits
        return response[2].toInt() and 0x7F
    }

    /**
     * Reads supported freeze frame PIDs.
     */
    private suspend fun readSupportedFreezeFramePIDs(frameNumber: Int): List<Int> {
        val supportedPIDs = mutableListOf<Int>()
        
        try {
            // Read supported PIDs (PID 0x00)
            val response = protocolManager.sendService(
                OBDConstants.SERVICE_READ_FREEZE_FRAME,
                byteArrayOf(0x00, frameNumber.toByte())
            )
            
            // Parse supported PIDs from bitmask
            if (response.size >= 6) {
                for (i in 0 until 32) {
                    val byteIndex = 2 + (i / 8)
                    val bitIndex = 7 - (i % 8)
                    if (byteIndex < response.size) {
                        if ((response[byteIndex].toInt() shr bitIndex) and 1 == 1) {
                            supportedPIDs.add(i + 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn(TAG, "Error reading supported freeze frame PIDs: ${e.message}")
        }
        
        return supportedPIDs
    }

    /**
     * Parses PID value from response.
     */
    private fun parsePIDValue(pid: Int, response: ByteArray): Any {
        // Basic parsing - would be expanded with full PID definitions
        return when (pid) {
            0x04 -> (response.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) * 100 / 255 // Engine load %
            0x05 -> (response.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) - 40 // Coolant temp °C
            0x0C -> { // RPM
                val a = response.getOrNull(2)?.toInt()?.and(0xFF) ?: 0
                val b = response.getOrNull(3)?.toInt()?.and(0xFF) ?: 0
                ((a * 256) + b) / 4
            }
            0x0D -> response.getOrNull(2)?.toInt()?.and(0xFF) ?: 0 // Vehicle speed km/h
            else -> response.drop(2).toByteArray()
        }
    }

    /**
     * Saves freeze frame to database.
     */
    private suspend fun saveFreezeFrameToDatabase(freezeFrame: FreezeFrame) {
        diagnosticLocalDataSource.saveFreezeFrame(freezeFrame)
    }

    /**
     * Parses ECU response into ECU object.
     */
    private fun parseECUResponse(address: Int, response: ByteArray): ECU {
        val type = ECUType.fromAddress(address)
        
        return ECU(
            address = address,
            type = type,
            name = type.description,
            shortName = type.prefix,
            discoveredAt = System.currentTimeMillis()
        )
    }

    /**
     * Enriches ECU with additional information.
     */
    private suspend fun enrichECUInfo(ecu: ECU): ECU {
        var enrichedECU = ecu
        
        try {
            // Read VIN
            val vinResponse = protocolManager.sendToAddress(
                ecu.address,
                OBDConstants.SERVICE_VEHICLE_INFO,
                byteArrayOf(0x02) // VIN PID
            )
            if (vinResponse.size > 3) {
                val vin = String(vinResponse.drop(3).toByteArray())
                enrichedECU = enrichedECU.copy(vin = vin)
            }
        } catch (e: Exception) {
            logger.debug(TAG, "Could not read VIN from ECU ${ecu.addressHex}")
        }
        
        try {
            // Read calibration ID
            val calResponse = protocolManager.sendToAddress(
                ecu.address,
                OBDConstants.SERVICE_VEHICLE_INFO,
                byteArrayOf(0x04) // Calibration ID PID
            )
            if (calResponse.size > 3) {
                val calId = String(calResponse.drop(3).toByteArray())
                enrichedECU = enrichedECU.copy(calibrationIds = listOf(calId))
            }
        } catch (e: Exception) {
            logger.debug(TAG, "Could not read calibration ID from ECU ${ecu.addressHex}")
        }
        
        return enrichedECU
    }

    /**
     * Generates report summary text.
     */
    private fun generateReportSummary(
        session: DiagnosticSession,
        dtcs: List<DTC>,
        ecus: List<ECU>
    ): String {
        return buildString {
            appendLine("=== Diagnostic Report Summary ===")
            appendLine()
            appendLine("Session: ${session.type.displayName}")
            appendLine("Date: ${dateFormatter.format(Date(session.startTime))}")
            appendLine("Duration: ${session.durationFormatted}")
            appendLine()
            appendLine("Vehicle: ${session.vin ?: "Unknown"}")
            session.mileage?.let { appendLine("Mileage: $it ${session.mileageUnit}") }
            appendLine()
            appendLine("ECUs Found: ${ecus.size}")
            ecus.forEach { appendLine("  - ${it.name} (${it.addressHex})") }
            appendLine()
            appendLine("DTCs Found: ${dtcs.size}")
            if (dtcs.isNotEmpty()) {
                appendLine("  Stored: ${dtcs.count { it.type == DTCType.STORED }}")
                appendLine("  Pending: ${dtcs.count { it.type == DTCType.PENDING }}")
                appendLine("  Permanent: ${dtcs.count { it.type == DTCType.PERMANENT }}")
                appendLine()
                appendLine("DTC Details:")
                dtcs.forEach { dtc ->
                    appendLine("  ${dtc.code}: ${dtc.description ?: "No description"}")
                }
            } else {
                appendLine("  No trouble codes found")
            }
            
            if (session.dtcsCleared) {
                appendLine()
                appendLine("Note: DTCs were cleared during this session")
            }
            
            session.notes?.let {
                appendLine()
                appendLine("Notes: $it")
            }
        }
    }

    /**
     * Exports report to PDF format.
     */
    private fun exportToPDF(report: DiagnosticReport): ByteArray {
        // PDF generation would use a library like iText or Android's PDF APIs
        // For now, return placeholder
        return "PDF export not implemented".toByteArray()
    }

    /**
     * Exports report to HTML format.
     */
    private fun exportToHTML(report: DiagnosticReport): ByteArray {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><title>Diagnostic Report</title>")
            appendLine("<style>")
            appendLine("body { font-family: Arial, sans-serif; margin: 20px; }")
            appendLine("h1 { color: #333; }")
            appendLine("table { border-collapse: collapse; width: 100%; }")
            appendLine("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
            appendLine("th { background-color: #4CAF50; color: white; }")
            appendLine(".dtc-code { font-weight: bold; color: #d9534f; }")
            appendLine("</style></head><body>")
            appendLine("<h1>SpaceTec Diagnostic Report</h1>")
            appendLine("<p>Generated: ${dateFormatter.format(Date(report.generatedAt))}</p>")
            appendLine("<h2>Vehicle Information</h2>")
            appendLine("<p>VIN: ${report.vin ?: "N/A"}</p>")
            report.mileage?.let { appendLine("<p>Mileage: $it ${report.mileageUnit}</p>") }
            appendLine("<h2>Diagnostic Trouble Codes</h2>")
            if (report.dtcs.isNotEmpty()) {
                appendLine("<table><tr><th>Code</th><th>Description</th><th>System</th></tr>")
                report.dtcs.forEach { dtc ->
                    appendLine("<tr>")
                    appendLine("<td class='dtc-code'>${dtc.code}</td>")
                    appendLine("<td>${dtc.description ?: "No description"}</td>")
                    appendLine("<td>${dtc.system}</td>")
                    appendLine("</tr>")
                }
                appendLine("</table>")
            } else {
                appendLine("<p>No trouble codes found</p>")
            }
            appendLine("<h2>ECUs Scanned</h2>")
            appendLine("<table><tr><th>Name</th><th>Address</th><th>Type</th></tr>")
            report.ecus.forEach { ecu ->
                appendLine("<tr>")
                appendLine("<td>${ecu.name}</td>")
                appendLine("<td>${ecu.addressHex}</td>")
                appendLine("<td>${ecu.type.description}</td>")
                appendLine("</tr>")
            }
            appendLine("</table>")
            appendLine("</body></html>")
        }
        return html.toByteArray(Charsets.UTF_8)
    }

    /**
     * Exports report to JSON format.
     */
    private fun exportToJSON(report: DiagnosticReport): ByteArray {
        // Using manual JSON construction to avoid additional dependencies
        val json = buildString {
            appendLine("{")
            appendLine("  \"id\": \"${report.id}\",")
            appendLine("  \"sessionId\": \"${report.sessionId}\",")
            appendLine("  \"vehicleId\": \"${report.vehicleId}\",")
            appendLine("  \"vin\": ${report.vin?.let { "\"$it\"" } ?: "null"},")
            appendLine("  \"generatedAt\": ${report.generatedAt},")
            appendLine("  \"sessionType\": \"${report.sessionType.name}\",")
            appendLine("  \"dtcs\": [")
            report.dtcs.forEachIndexed { index, dtc ->
                append("    {\"code\": \"${dtc.code}\", \"description\": \"${dtc.description ?: ""}\"}")
                if (index < report.dtcs.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  ],")
            appendLine("  \"ecus\": [")
            report.ecus.forEachIndexed { index, ecu ->
                append("    {\"name\": \"${ecu.name}\", \"address\": ${ecu.address}}")
                if (index < report.ecus.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        return json.toByteArray(Charsets.UTF_8)
    }

    /**
     * Exports report to CSV format.
     */
    private fun exportToCSV(report: DiagnosticReport): ByteArray {
        val csv = buildString {
            appendLine("SpaceTec Diagnostic Report")
            appendLine("Generated,${dateFormatter.format(Date(report.generatedAt))}")
            appendLine("VIN,${report.vin ?: "N/A"}")
            appendLine()
            appendLine("DTCs")
            appendLine("Code,Description,System,Type")
            report.dtcs.forEach { dtc ->
                appendLine("${dtc.code},\"${dtc.description ?: ""}\",${dtc.system},${dtc.type}")
            }
            appendLine()
            appendLine("ECUs")
            appendLine("Name,Address,Type")
            report.ecus.forEach { ecu ->
                appendLine("${ecu.name},${ecu.addressHex},${ecu.type}")
            }
        }
        return csv.toByteArray(Charsets.UTF_8)
    }

    /**
     * Exports report to plain text format.
     */
    private fun exportToText(report: DiagnosticReport): ByteArray {
        return report.summary.toByteArray(Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "DiagnosticRepository"
    }
}

// ==================== Supporting Data Classes ====================

/**
 * Result container for all DTC types.
 * 
 * @property stored List of stored/confirmed DTCs
 * @property pending List of pending/maturing DTCs
 * @property permanent List of permanent DTCs
 * @property timestamp When the DTCs were read
 */
data class AllDTCsResult(
    val stored: List<DTC>,
    val pending: List<DTC>,
    val permanent: List<DTC>,
    val timestamp: Long
) {
    /** All DTCs combined */
    val allDTCs: List<DTC>
        get() = stored + pending + permanent
    
    /** Total count of all DTCs */
    val totalCount: Int
        get() = stored.size + pending.size + permanent.size
    
    /** Whether any DTCs are present */
    val hasDTCs: Boolean
        get() = totalCount > 0
}

/**
 * Represents a historical occurrence of a DTC.
 * 
 * @property dtc The DTC that occurred
 * @property sessionId Session when it was detected
 * @property timestamp When it was detected
 * @property mileage Vehicle mileage when detected
 * @property wasCleared Whether it was cleared in the session
 */
data class DTCOccurrence(
    val dtc: DTC,
    val sessionId: String,
    val timestamp: Long,
    val mileage: Int? = null,
    val wasCleared: Boolean = false
)

/**
 * Report export formats.
 */
enum class ReportFormat {
    /** Portable Document Format */
    PDF,
    
    /** HyperText Markup Language */
    HTML,
    
    /** JavaScript Object Notation */
    JSON,
    
    /** Comma-Separated Values */
    CSV,
    
    /** Plain text */
    TXT
}

// ==================== Qualifier Annotation ====================

/**
 * Qualifier for IO dispatcher injection.
 */
@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
