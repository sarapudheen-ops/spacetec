package com.spacetec.data.repository

import com.spacetec.domain.models.vehicle.Vehicle
import com.spacetec.domain.models.vehicle.VehicleDtc
import com.spacetec.domain.repository.VehicleRepository
import com.spacetec.data.datasource.device.OBDDataSource
import com.spacetec.core.common.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of VehicleRepository that handles vehicle-related data operations
 * using OBDDataSource for communication with the vehicle.
 */
class VehicleRepositoryImpl @Inject constructor(
    private val obdDataSource: OBDDataSource
) : VehicleRepository {

    override suspend fun connectToVehicle(): Result<Vehicle> {
        return try {
            // Initialize communication with the vehicle
            val initResult = obdDataSource.initialize()
            if (!initResult.isSuccess) {
                return Result.Failure(Exception("Failed to initialize communication with vehicle"))
            }

            // Read vehicle identification
            val vinResult = obdDataSource.readVIN()
            if (!vinResult.isSuccess) {
                return Result.Failure(Exception("Failed to read vehicle identification"))
            }

            val vin = vinResult.data ?: return Result.Failure(Exception("No VIN received"))

            // Create vehicle object with basic info
            val vehicle = Vehicle(
                vin = vin,
                make = detectVehicleMake(vin),
                model = "",
                year = detectVehicleYear(vin),
                engine = "",
                supportedPids = obdDataSource.detectSupportedPids().getOrNull() ?: emptyList()
            )

            Result.Success(vehicle)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun readVehicleInfo(): Result<Vehicle> {
        return try {
            val vinResult = obdDataSource.readVIN()
            if (!vinResult.isSuccess) {
                return Result.Failure(Exception("Failed to read vehicle identification"))
            }

            val vin = vinResult.data ?: return Result.Failure(Exception("No VIN received"))

            // Attempt to read additional vehicle info if available
            val supportedPids = obdDataSource.detectSupportedPids().getOrNull() ?: emptyList()

            val vehicle = Vehicle(
                vin = vin,
                make = detectVehicleMake(vin),
                model = "",
                year = detectVehicleYear(vin),
                engine = readEngineInfo(supportedPids).getOrNull() ?: "",
                supportedPids = supportedPids
            )

            Result.Success(vehicle)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun readDTCs(): Result<List<VehicleDtc>> {
        return try {
            val dtcStrings = obdDataSource.readDTCs()
            if (!dtcStrings.isSuccess) {
                return Result.Failure(Exception("Failed to read DTCs"))
            }

            val dtcList = dtcStrings.data?.map { dtcCode ->
                // For now, create a basic DTC object - in a real implementation,
                // this would fetch detailed information from a DTC database
                VehicleDtc(
                    code = dtcCode,
                    description = "DTC $dtcCode - Description not available",
                    severity = "MEDIUM",
                    status = "ACTIVE"
                )
            } ?: emptyList()

            Result.Success(dtcList)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun clearDTCs(): Result<Boolean> {
        return try {
            val result = obdDataSource.clearDTCs()
            Result.Success(result.isSuccess)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun readLiveData(pids: List<Int>): Flow<Result<Map<Int, String>>> {
        return flow {
            val results = mutableMapOf<Int, String>()
            
            for (pid in pids) {
                try {
                    val result = obdDataSource.readLiveData(pid)
                    if (result.isSuccess) {
                        results[pid] = result.data ?: ""
                    } else {
                        results[pid] = "Error reading PID $pid"
                    }
                } catch (e: Exception) {
                    results[pid] = "Exception reading PID $pid: ${e.message}"
                }
                
                // Emit partial results as they become available
                emit(Result.Success(results.toMap()))
            }
        }
    }

    override suspend fun disconnect(): Result<Boolean> {
        // In a real implementation, this would handle proper disconnection
        // For now, just return success
        return Result.Success(true)
    }

    private fun detectVehicleMake(vin: String): String {
        if (vin.length < 3) return "Unknown"
        
        val makeCode = vin.substring(0, 3).uppercase()
        return when (makeCode[0]) {
            'W' -> "Volkswagen Group (VW, Audi, Porsche, etc.)"
            '1', '4', '5' -> "General Motors (Chevrolet, GMC, Cadillac, etc.)"
            '2' -> "Canada (GM, Ford, etc.)"
            '3' -> "FCA (Chrysler, Dodge, Jeep, etc.)"
            'J' -> "Japan (Nissan, Toyota, etc.)"
            'K' -> "South Korea"
            'L' -> "China"
            'S' -> "UK (Jaguar, Land Rover, etc.)"
            'V' -> "France/Spain (Peugeot, Renault, etc.)"
            'Z' -> "Italy (Fiat, Alfa Romeo, etc.)"
            '9' -> "South America"
            else -> "Unknown"
        }
    }

    private fun detectVehicleYear(vin: String): Int {
        if (vin.length < 10) return 0
        
        val yearCode = vin[9]
        return when (yearCode) {
            in 'A'..'H' -> 1980 + (yearCode - 'A')
            'J' -> 1988
            'K' -> 1989
            'L' -> 1990
            'M' -> 1991
            'N' -> 1992
            'P' -> 1993
            'R' -> 1994
            'S' -> 1995
            'T' -> 1996
            'V' -> 1997
            'W' -> 1998
            'X' -> 1999
            'Y' -> 2000
            '1' -> 2001
            '2' -> 2002
            '3' -> 2003
            '4' -> 2004
            '5' -> 2005
            '6' -> 2006
            '7' -> 2007
            '8' -> 2008
            '9' -> 2009
            else -> 0
        }
    }

    private suspend fun readEngineInfo(supportedPids: List<Int>): Result<String> {
        // Check if PID 12 (Engine type) is supported
        if (12 in supportedPids) {
            val result = obdDataSource.readLiveData(12)
            if (result.isSuccess) {
                return Result.Success(result.data ?: "Unknown")
            }
        }
        
        // Try other engine-related PIDs if available
        return Result.Success("Engine info not available")
    }
}