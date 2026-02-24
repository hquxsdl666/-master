package com.tcmpulse.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "pulse_records", indexes = [
    Index(name = "idx_user_time", columnList = "user_id, record_time"),
    Index(name = "idx_main_pulse", columnList = "main_pulse"),
    Index(name = "idx_syndrome", columnList = "syndrome")
])
data class PulseRecord(
    @Id
    @Column(name = "id", length = 36)
    val id: String = UUID.randomUUID().toString(),
    
    @Column(name = "user_id", length = 36, nullable = false)
    val userId: String,
    
    @Column(name = "device_id", length = 50, nullable = false)
    val deviceId: String,
    
    // 基本信息
    @Column(name = "record_time", nullable = false)
    val recordTime: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "measurement_duration")
    val measurementDuration: Int = 60, // 秒
    
    @Column(name = "sample_rate")
    val sampleRate: Int = 100, // Hz
    
    // 信号质量
    @Column(name = "signal_quality", precision = 3, scale = 2)
    val signalQuality: BigDecimal? = null,
    
    @Column(name = "valid_segments")
    val validSegments: Int? = null,
    
    @Column(name = "total_segments")
    val totalSegments: Int? = null,
    
    // 脉象识别结果
    @Column(name = "main_pulse", length = 20)
    val mainPulse: String? = null,
    
    @Column(name = "secondary_pulse", length = 20)
    val secondaryPulse: String? = null,
    
    @Column(name = "pulse_rate")
    val pulseRate: Int? = null,
    
    // 脉位特征
    @Column(name = "position_floating", precision = 3, scale = 2)
    val positionFloating: BigDecimal? = null,
    
    @Column(name = "position_normal", precision = 3, scale = 2)
    val positionNormal: BigDecimal? = null,
    
    @Column(name = "position_deep", precision = 3, scale = 2)
    val positionDeep: BigDecimal? = null,
    
    // 脉率特征
    @Column(name = "rate_category", length = 10)
    val rateCategory: String? = null, // slow, normal, fast, rapid
    
    @Column(name = "rhythm_regularity", precision = 3, scale = 2)
    val rhythmRegularity: BigDecimal? = null,
    
    @Column(name = "has_intermittent")
    val hasIntermittent: Boolean = false,
    
    // 脉力特征
    @Column(name = "force_score", precision = 3, scale = 2)
    val forceScore: BigDecimal? = null,
    
    @Column(name = "systolic_amplitude", precision = 6, scale = 3)
    val systolicAmplitude: BigDecimal? = null,
    
    @Column(name = "diastolic_amplitude", precision = 6, scale = 3)
    val diastolicAmplitude: BigDecimal? = null,
    
    // 脉形特征
    @Column(name = "width_score", precision = 3, scale = 2)
    val widthScore: BigDecimal? = null,
    
    @Column(name = "length_score", precision = 3, scale = 2)
    val lengthScore: BigDecimal? = null,
    
    @Column(name = "smoothness", precision = 3, scale = 2)
    val smoothness: BigDecimal? = null,
    
    @Column(name = "tautness", precision = 3, scale = 2)
    val tautness: BigDecimal? = null,
    
    @Column(name = "fullness", precision = 3, scale = 2)
    val fullness: BigDecimal? = null,
    
    @Column(name = "hollowness", precision = 3, scale = 2)
    val hollowness: BigDecimal? = null,
    
    // 脉势特征
    @Column(name = "rising_slope", precision = 6, scale = 3)
    val risingSlope: BigDecimal? = null,
    
    @Column(name = "falling_slope", precision = 6, scale = 3)
    val fallingSlope: BigDecimal? = null,
    
    @Column(name = "dicrotic_notch_depth", precision = 6, scale = 3)
    val dicroticNotchDepth: BigDecimal? = null,
    
    @Column(name = "wave_area", precision = 8, scale = 3)
    val waveArea: BigDecimal? = null,
    
    // 频域特征
    @Column(name = "dominant_freq", precision = 4, scale = 2)
    val dominantFreq: BigDecimal? = null,
    
    @Column(name = "lf_power", precision = 8, scale = 3)
    val lfPower: BigDecimal? = null,
    
    @Column(name = "hf_power", precision = 8, scale = 3)
    val hfPower: BigDecimal? = null,
    
    @Column(name = "lf_hf_ratio", precision = 5, scale = 2)
    val lfHfRatio: BigDecimal? = null,
    
    // 辨证结果
    @Column(name = "syndrome", length = 50)
    val syndrome: String? = null,
    
    @Column(name = "syndrome_confidence", precision = 3, scale = 2)
    val syndromeConfidence: BigDecimal? = null,
    
    // 原始数据存储
    @Column(name = "ppg_data_url", length = 255)
    val ppgDataUrl: String? = null,
    
    @Column(name = "feature_vector", columnDefinition = "TEXT")
    val featureVector: String? = null, // JSON格式
    
    // 元数据
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "synced_at")
    val syncedAt: LocalDateTime? = null,
    
    @Column(name = "sync_status")
    val syncStatus: Int = 0 // 0: 未同步, 1: 已同步, 2: 同步失败
)
