package com.nocturne.audiocapture.api

import com.nocturne.audiocapture.api.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

data class ChunkUploadResponse(val chunk_id: String, val status: String)
data class CorrectionRequest(val corrected_word: String)

interface ApiService {
    @Multipart @POST("audio/chunks")
    suspend fun uploadChunk(
        @Header("X-API-Key") key: String,
        @Part audio: MultipartBody.Part,
        @Part("device_id") deviceId: RequestBody,
        @Part("chunk_seq") chunkSeq: RequestBody,
        @Part("recorded_at") recordedAt: RequestBody,
        @Part("markers") markers: RequestBody
    ): Response<ChunkUploadResponse>

    @GET("today/status") suspend fun getTodayStatus(): TodayStatus
    @GET("sessions") suspend fun getSessions(@Query("date") date: String? = null, @Query("limit") limit: Int = 20): List<Session>
    @GET("sessions/{id}/transcript") suspend fun getTranscript(@Path("id") id: String): TranscriptResponse
    @Streaming @GET("sessions/{id}/audio") suspend fun getAudio(@Path("id") id: String): Response<ResponseBody>
    @POST("ask") suspend fun ask(@Body body: AskRequest): AskResponse
    @GET("health") suspend fun getHealth(): HealthStatus
    @PUT("sessions/{id}/words/{index}") suspend fun correctWord(@Path("id") id: String, @Path("index") index: Int, @Body body: CorrectionRequest): Response<Unit>
}
