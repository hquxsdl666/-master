package com.tcmpulse.pulseapp.data.api

import com.tcmpulse.pulseapp.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface PulseApiService {
    
    // 脉象分析
    @POST("api/v1/pulse/analyze")
    suspend fun analyzePulse(@Body request: PulseAnalysisRequest): Response<PulseAnalysisResponse>
    
    // 保存脉诊记录
    @POST("api/v1/pulse/records")
    suspend fun savePulseRecord(@Body request: SavePulseRecordRequest): Response<PulseRecord>
    
    // 获取脉诊记录列表
    @GET("api/v1/pulse/records")
    suspend fun getPulseRecords(
        @Query("userId") userId: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): Response<PageResponse<PulseRecord>>
    
    // 获取脉诊记录详情
    @GET("api/v1/pulse/records/{id}")
    suspend fun getPulseRecordDetail(@Path("id") id: String): Response<PulseRecordDetail>
    
    // 获取脉象统计
    @GET("api/v1/pulse/statistics/{userId}")
    suspend fun getPulseStatistics(@Path("userId") userId: String): Response<PulseStatistics>
    
    // 获取方剂推荐
    @POST("api/v1/prescriptions/recommend")
    suspend fun getPrescriptionRecommendations(
        @Body request: PrescriptionRecommendationRequest
    ): Response<PrescriptionRecommendationResponse>
    
    // 获取脉象类型列表
    @GET("api/v1/pulse/types")
    suspend fun getPulseTypes(): Response<List<PulseTypeInfo>>
}

// 请求数据类
data class SavePulseRecordRequest(
    val userId: String,
    val deviceId: String,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeatures,
    val syndrome: String?,
    val syndromeConfidence: java.math.BigDecimal?,
    val ppgData: String?
)

data class PrescriptionRecommendationRequest(
    val pulseRecordId: String,
    val mainPulse: String,
    val secondaryPulse: String?,
    val syndrome: String?
)

// 响应数据类
data class PageResponse<T>(
    val total: Long,
    val pages: Int,
    val current: Int,
    val size: Int,
    val records: List<T>
)

data class PrescriptionRecommendationResponse(
    val syndrome: String?,
    val syndromeConfidence: java.math.BigDecimal?,
    val recommendations: List<PrescriptionRecommendation>
)
