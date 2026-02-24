package com.tcmpulse.controller

import com.tcmpulse.dto.*
import com.tcmpulse.service.PulseAnalysisService
import com.tcmpulse.service.PulseRecordService
import com.tcmpulse.service.PrescriptionRecommendationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Base64

@RestController
@RequestMapping("/api/v1/pulse")
@Tag(name = "脉诊管理", description = "脉象采集、分析、记录相关接口")
class PulseController(
    private val pulseAnalysisService: PulseAnalysisService,
    private val pulseRecordService: PulseRecordService,
    private val prescriptionRecommendationService: PrescriptionRecommendationService
) {
    
    @PostMapping("/analyze")
    @Operation(summary = "分析脉象数据", description = "上传PPG数据进行脉象分析")
    fun analyzePulse(@RequestBody request: PulseAnalysisRequest): ResponseEntity<PulseAnalysisResponse> {
        // 解码PPG数据
        val ppgBytes = Base64.getDecoder().decode(request.ppgData)
        val ppgData = ppgBytes.map { it.toDouble() }.toDoubleArray()
        
        // 分析脉象
        val result = pulseAnalysisService.analyzePulse(ppgData, request.sampleRate)
        
        return ResponseEntity.ok(result)
    }
    
    @PostMapping("/records")
    @Operation(summary = "保存脉诊记录", description = "保存脉诊记录到数据库")
    fun savePulseRecord(@RequestBody request: SavePulseRecordRequest): ResponseEntity<PulseRecordResponse> {
        val record = pulseRecordService.saveRecord(request)
        return ResponseEntity.ok(record)
    }
    
    @GetMapping("/records")
    @Operation(summary = "获取脉诊记录列表", description = "分页获取用户的脉诊记录")
    fun getPulseRecords(
        @RequestParam userId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<PulseRecordResponse>> {
        val records = pulseRecordService.getRecords(userId, page, size)
        return ResponseEntity.ok(records)
    }
    
    @GetMapping("/records/{id}")
    @Operation(summary = "获取脉诊记录详情", description = "获取单条脉诊记录的详细信息")
    fun getPulseRecordDetail(@PathVariable id: String): ResponseEntity<PulseRecordDetailResponse> {
        val record = pulseRecordService.getRecordDetail(id)
        return ResponseEntity.ok(record)
    }
    
    @GetMapping("/statistics/{userId}")
    @Operation(summary = "获取脉象统计", description = "获取用户的脉象统计数据")
    fun getPulseStatistics(@PathVariable userId: String): ResponseEntity<PulseStatisticsResponse> {
        val statistics = pulseRecordService.getStatistics(userId)
        return ResponseEntity.ok(statistics)
    }
    
    @PostMapping("/recommendations")
    @Operation(summary = "获取方剂推荐", description = "根据脉象结果推荐方剂")
    fun getPrescriptionRecommendations(
        @RequestBody request: PrescriptionRecommendationRequest
    ): ResponseEntity<PrescriptionRecommendationResponse> {
        val recommendations = prescriptionRecommendationService.recommend(request)
        return ResponseEntity.ok(recommendations)
    }
    
    @GetMapping("/types")
    @Operation(summary = "获取脉象类型列表", description = "获取所有支持的脉象类型")
    fun getPulseTypes(): ResponseEntity<List<PulseTypeInfo>> {
        val types = listOf(
            PulseTypeInfo("浮脉", "浮脉类", "轻按即得，重按稍减", listOf("表证")),
            PulseTypeInfo("沉脉", "沉脉类", "轻按不得，重按始得", listOf("里证")),
            PulseTypeInfo("迟脉", "迟脉类", "一息不足四至", listOf("寒证")),
            PulseTypeInfo("数脉", "数脉类", "一息五至以上", listOf("热证")),
            PulseTypeInfo("虚脉", "虚脉类", "举按无力，应指松软", listOf("虚证")),
            PulseTypeInfo("实脉", "实脉类", "举按有力，应指充实", listOf("实证")),
            PulseTypeInfo("滑脉", "实脉类", "往来流利，如珠走盘", listOf("痰饮", "食滞", "妊娠")),
            PulseTypeInfo("涩脉", "迟脉类", "艰涩不畅，如轻刀刮竹", listOf("气滞", "血瘀")),
            PulseTypeInfo("弦脉", "实脉类", "端直以长，如按琴弦", listOf("肝胆病", "疼痛", "痰饮")),
            PulseTypeInfo("细脉", "虚脉类", "脉细如线，应指明显", listOf("气血两虚", "湿邪")),
            PulseTypeInfo("洪脉", "浮脉类", "浮大有力，来盛去衰", listOf("热盛")),
            PulseTypeInfo("紧脉", "实脉类", "绷急弹指，状如转索", listOf("寒证", "痛证"))
        )
        return ResponseEntity.ok(types)
    }
}

// DTO类
data class SavePulseRecordRequest(
    val userId: String,
    val deviceId: String,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeaturesDto,
    val syndrome: String?,
    val syndromeConfidence: java.math.BigDecimal?,
    val ppgData: String?
)

data class PulseRecordResponse(
    val id: String,
    val userId: String,
    val recordTime: String,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val syndrome: String?,
    val confidence: java.math.BigDecimal?
)

data class PulseRecordDetailResponse(
    val id: String,
    val userId: String,
    val deviceId: String,
    val recordTime: String,
    val measurementDuration: Int,
    val signalQuality: java.math.BigDecimal?,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeaturesDto,
    val syndrome: String?,
    val syndromeConfidence: java.math.BigDecimal?
)

data class PageResponse<T>(
    val total: Long,
    val pages: Int,
    val current: Int,
    val size: Int,
    val records: List<T>
)

data class PulseStatisticsResponse(
    val totalRecords: Long,
    val avgPulseRate: Double?,
    val pulseTypeDistribution: Map<String, Long>,
    val recentTrend: String
)

data class PrescriptionRecommendationRequest(
    val pulseRecordId: String,
    val mainPulse: String,
    val secondaryPulse: String?,
    val syndrome: String?
)

data class PrescriptionRecommendationResponse(
    val syndrome: String?,
    val syndromeConfidence: java.math.BigDecimal?,
    val recommendations: List<PrescriptionRecommendationDto>
)

data class PrescriptionRecommendationDto(
    val prescriptionId: Long,
    val name: String,
    val composition: List<HerbDosageDto>,
    val efficacy: String,
    matchScore: java.math.BigDecimal,
    val rank: Int
)

data class HerbDosageDto(
    val herb: String,
    val dosage: String,
    val role: String
)

data class PulseTypeInfo(
    val name: String,
    val category: String,
    val description: String,
    val indications: List<String>
)
