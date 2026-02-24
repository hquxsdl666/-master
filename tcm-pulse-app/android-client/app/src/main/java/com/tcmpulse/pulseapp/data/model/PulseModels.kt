package com.tcmpulse.pulseapp.data.model

import java.math.BigDecimal
import java.time.LocalDateTime

// 脉象分析请求
data class PulseAnalysisRequest(
    val ppgData: String,
    val sampleRate: Int = 100,
    val duration: Int = 60
)

// 脉象分析响应
data class PulseAnalysisResponse(
    val mainPulse: String,
    val mainPulseConfidence: BigDecimal,
    val secondaryPulse: String?,
    val secondaryPulseConfidence: BigDecimal?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeatures,
    val syndrome: String?,
    val syndromeConfidence: BigDecimal?,
    val allProbabilities: List<PulseProbability>
)

// 脉象特征
data class PulseFeatures(
    val position: PositionFeatures,
    val rate: RateFeatures,
    val force: ForceFeatures,
    val shape: ShapeFeatures,
    val momentum: MomentumFeatures
)

// 脉位特征
data class PositionFeatures(
    val floating: BigDecimal,
    val normal: BigDecimal,
    val deep: BigDecimal
)

// 脉率特征
data class RateFeatures(
    val rateValue: Int,
    val category: String,
    val rhythmRegularity: BigDecimal,
    val intermittent: Boolean
)

// 脉力特征
data class ForceFeatures(
    val forceScore: BigDecimal,
    val systolicAmplitude: BigDecimal,
    val diastolicAmplitude: BigDecimal
)

// 脉形特征
data class ShapeFeatures(
    val widthScore: BigDecimal,
    val lengthScore: BigDecimal,
    val smoothness: BigDecimal,
    val tautness: BigDecimal,
    val fullness: BigDecimal,
    val hollowness: BigDecimal
)

// 脉势特征
data class MomentumFeatures(
    val risingSlope: BigDecimal,
    val fallingSlope: BigDecimal,
    val dicroticNotchDepth: BigDecimal,
    val waveArea: BigDecimal
)

// 脉象概率
data class PulseProbability(
    val pulse: String,
    val probability: BigDecimal
)

// 脉诊记录
data class PulseRecord(
    val id: String,
    val userId: String,
    val deviceId: String,
    val recordTime: LocalDateTime,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val syndrome: String?,
    val confidence: BigDecimal?,
    val signalQuality: BigDecimal?
)

// 脉诊记录详情
data class PulseRecordDetail(
    val id: String,
    val userId: String,
    val deviceId: String,
    val recordTime: LocalDateTime,
    val measurementDuration: Int,
    val signalQuality: BigDecimal?,
    val mainPulse: String,
    val secondaryPulse: String?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeatures,
    val syndrome: String?,
    val syndromeConfidence: BigDecimal?
)

// 方剂推荐
data class PrescriptionRecommendation(
    val prescriptionId: Long,
    val name: String,
    val composition: List<HerbDosage>,
    val efficacy: String,
    val matchScore: BigDecimal,
    val rank: Int
)

// 药材剂量
data class HerbDosage(
    val herb: String,
    val dosage: String,
    val role: String
)

// 脉象类型信息
data class PulseTypeInfo(
    val name: String,
    val category: String,
    val description: String,
    val indications: List<String>
)

// 脉象统计
data class PulseStatistics(
    val totalRecords: Long,
    val avgPulseRate: Double?,
    val pulseTypeDistribution: Map<String, Long>,
    val recentTrend: String
)

// 扫描到的蓝牙设备信息
data class ScannedDeviceInfo(
    val address: String,
    val name: String,
    val rssi: Int,
    /** true = 系统已配对设备（无需扫描，立即可用） */
    val isBonded: Boolean = false
)

// 脉象采集状态
sealed class PulseCollectionState {
    object Idle : PulseCollectionState()
    /** 正在扫描 BLE 心率广播，等待手表信号 */
    data class Scanning(val sourceDevice: String = "") : PulseCollectionState()
    object Collecting : PulseCollectionState()
    data class Progress(val percent: Int, val quality: Float, val bpm: Int = 0) : PulseCollectionState()
    object Analyzing : PulseCollectionState()
    data class Success(val result: PulseAnalysisResponse) : PulseCollectionState()
    data class Error(val message: String) : PulseCollectionState()
}
