package com.example.iosgalaxywatchsync.network

import com.example.iosgalaxywatchsync.models.ServerHealthCheck
import com.example.iosgalaxywatchsync.models.SyncRequest
import com.example.iosgalaxywatchsync.models.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** API interface for local server communication */
interface LocalServerApi {

    @GET("/api/v1/health") suspend fun healthCheck(): Response<ServerHealthCheck>

    @POST("/api/v1/data") suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>

    @GET("/api/v1/data")
    suspend fun getData(
            @Query("device_id") deviceId: String,
            @Query("since") timestamp: Long
    ): Response<SyncResponse>
}
