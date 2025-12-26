package com.spacetec.core.network.api

import com.spacetec.core.network.model.DTCDefinitionResponse
import com.spacetec.core.network.model.VehicleInfoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SpaceTecApi {
    
    @GET("dtc/{code}")
    suspend fun getDTCDefinition(@Path("code") code: String): Response<DTCDefinitionResponse>
    
    @GET("dtc/search")
    suspend fun searchDTCs(@Query("q") query: String): Response<List<DTCDefinitionResponse>>
    
    @GET("vehicle/{vin}")
    suspend fun getVehicleInfo(@Path("vin") vin: String): Response<VehicleInfoResponse>
    
    @GET("dtc/updates")
    suspend fun getDTCUpdates(@Query("since") timestamp: Long): Response<List<DTCDefinitionResponse>>
}
