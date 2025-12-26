package com.spacetec.core.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface SpaceTecApi {
    @GET("dtc/{code}")
    suspend fun getDTCInfo(@Path("code") code: String): DTCInfoResponse
    
    @GET("vehicle/{vin}")
    suspend fun getVehicleInfo(@Path("vin") vin: String): VehicleInfoResponse
    
    @POST("reports")
    suspend fun uploadReport(@Body report: ReportUpload): UploadResponse
    
    @GET("tsb/{dtcCode}")
    suspend fun getTSBs(@Path("dtcCode") code: String): List<TSBResponse>
}

data class DTCInfoResponse(val code: String, val description: String, val causes: List<String>, val fixes: List<String>)
data class VehicleInfoResponse(val vin: String, val make: String, val model: String, val year: Int)
data class ReportUpload(val vin: String, val dtcs: List<String>, val timestamp: Long)
data class UploadResponse(val id: String, val url: String)
data class TSBResponse(val id: String, val title: String, val description: String)

// API service instance
object SpaceTecApiService {
    private const val BASE_URL = "https://api.spacetec.app/v1/"
    
    val api: SpaceTecApi by lazy {
        NetworkClient.retrofit.newBuilder()
            .baseUrl(BASE_URL)
            .build()
            .create(SpaceTecApi::class.java)
    }
}