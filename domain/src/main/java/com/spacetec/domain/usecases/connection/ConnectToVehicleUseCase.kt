package com.spacetec.domain.usecases.connection

import com.spacetec.domain.models.vehicle.Vehicle
import com.spacetec.domain.repository.VehicleRepository
import com.spacetec.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for connecting to a vehicle via OBD scanner.
 *
 * This use case orchestrates the vehicle connection process, including
 * scanner initialization, vehicle identification, and connection establishment.
 */
class ConnectToVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository
) {
    /**
     * Represents the different states of the connection process
     */
    sealed class ConnectionState {
        object Initial : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
        data class Progress(val step: String, val progress: Int) : ConnectionState()
    }

    /**
     * Connect to a vehicle and return a flow of connection states
     */
    operator fun invoke(): Flow<Result<Vehicle>> = flow {
        try {
            emit(Result.Loading(ConnectionState.Connecting))

            // Step 1: Initialize connection to vehicle
            val connectionResult = vehicleRepository.connectToVehicle()
            
            if (connectionResult.isSuccess) {
                val vehicle = connectionResult.getOrNull()
                if (vehicle != null) {
                    emit(Result.Success(vehicle))
                } else {
                    emit(Result.Failure(Exception("Connection successful but no vehicle data received")))
                }
            } else {
                emit(Result.Failure(connectionResult.exceptionOrNull() ?: Exception("Unknown connection error")))
            }
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }

    /**
     * Connect to vehicle with detailed progress reporting
     */
    fun connectWithProgress(): Flow<ConnectionState> = flow {
        try {
            // Emit initial state
            emit(ConnectionState.Initial)
            
            // Step 1: Connecting
            emit(ConnectionState.Progress("Initializing scanner connection...", 20))
            emit(ConnectionState.Connecting)
            
            // Step 2: Attempt connection
            emit(ConnectionState.Progress("Connecting to vehicle...", 40))
            val connectionResult = vehicleRepository.connectToVehicle()
            
            if (connectionResult.isSuccess) {
                emit(ConnectionState.Progress("Reading vehicle information...", 70))
                
                // Step 3: Read vehicle info
                val vehicleInfoResult = vehicleRepository.readVehicleInfo()
                if (vehicleInfoResult.isSuccess) {
                    emit(ConnectionState.Progress("Connection established", 100))
                    emit(ConnectionState.Connected)
                } else {
                    emit(ConnectionState.Error("Failed to read vehicle information: ${vehicleInfoResult.exceptionOrNull()?.message}"))
                }
            } else {
                emit(ConnectionState.Error("Connection failed: ${connectionResult.exceptionOrNull()?.message}"))
            }
        } catch (e: Exception) {
            emit(ConnectionState.Error("Connection error: ${e.message}"))
        }
    }

    /**
     * Disconnect from the vehicle
     */
    suspend fun disconnect(): Result<Boolean> {
        return try {
            vehicleRepository.disconnect()
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * Verify if the connection to the vehicle is still active
     */
    suspend fun isVehicleConnected(): Boolean {
        // In a real implementation, this would send a ping command to verify connection
        // For now, we'll assume the repository can determine connection status
        return try {
            // Attempt a simple read to verify connection
            val result = vehicleRepository.readVehicleInfo()
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reconnect to the vehicle if connection is lost
     */
    suspend fun reconnect(): Flow<Result<Vehicle>> = flow {
        try {
            // First, try to disconnect if currently connected
            vehicleRepository.disconnect()
            
            // Then, attempt to reconnect
            val connectionResult = vehicleRepository.connectToVehicle()
            
            if (connectionResult.isSuccess) {
                val resultValue = connectionResult.getOrNull()
                if (resultValue != null) {
                    emit(Result.Success(resultValue))
                } else {
                    emit(Result.Failure(Exception("Connection result was successful but value was null")))
                }
            } else {
                emit(Result.Failure(connectionResult.exceptionOrNull() ?: Exception("Reconnection failed")))
            }
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
}