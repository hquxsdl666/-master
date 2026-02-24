package com.tcmpulse.service

import com.tcmpulse.controller.*
import com.tcmpulse.dto.PulseFeaturesDto
import com.tcmpulse.entity.PulseRecord
import com.tcmpulse.repository.PulseRecordRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PulseRecordService(
    private val pulseRecordRepository: PulseRecordRepository
) {
    
    fun saveRecord(request: SavePulseRecordRequest): PulseRecordResponse {
        val record = PulseRecord(
            userId = request.userId,
            deviceId = "unknown",
            mainPulse = request.mainPulse,
            secondaryPulse = request.secondaryPulse,
            pulseRate = request.pulseRate,
            syndrome = request.syndrome,
            syndromeConfidence = request.syndromeConfidence,
            positionFloating = request.pulseFeatures.position.floating,
            positionNormal = request.pulseFeatures.position.normal,
            positionDeep = request.pulseFeatures.position.deep,
            rateCategory = request.pulseFeatures.rate.category,
            rhythmRegularity = request.pulseFeatures.rate.rhythmRegularity,
            hasIntermittent = request.pulseFeatures.rate.intermittent,
            forceScore = request.pulseFeatures.force.forceScore,
            systolicAmplitude = request.pulseFeatures.force.systolicAmplitude,
            diastolicAmplitude = request.pulseFeatures.force.diastolicAmplitude,
            widthScore = request.pulseFeatures.shape.widthScore,
            lengthScore = request.pulseFeatures.shape.lengthScore,
            smoothness = request.pulseFeatures.shape.smoothness,
            tautness = request.pulseFeatures.shape.tautness,
            fullness = request.pulseFeatures.shape.fullness,
            hollowness = request.pulseFeatures.shape.hollowness,
            risingSlope = request.pulseFeatures.momentum.risingSlope,
            fallingSlope = request.pulseFeatures.momentum.fallingSlope,
            dicroticNotchDepth = request.pulseFeatures.momentum.dicroticNotchDepth,
            waveArea = request.pulseFeatures.momentum.waveArea
        )
        
        val savedRecord = pulseRecordRepository.save(record)
        return mapToResponse(savedRecord)
    }
    
    fun getRecords(userId: String, page: Int, size: Int): PageResponse<PulseRecordResponse> {
        val pageable = PageRequest.of(page - 1, size)
        val result = pulseRecordRepository.findByUserId(userId, pageable)
        
        return PageResponse(
            total = result.totalElements,
            pages = result.totalPages,
            current = page,
            size = size,
            records = result.content.map { mapToResponse(it) }
        )
    }
    
    fun getRecordDetail(id: String): PulseRecordDetailResponse {
        val record = pulseRecordRepository.findById(id)
            .orElseThrow { RuntimeException("记录不存在") }
        
        return mapToDetailResponse(record)
    }
    
    fun getStatistics(userId: String): PulseStatisticsResponse {
        val totalRecords = pulseRecordRepository.countByUserId(userId)
        val avgPulseRate = pulseRecordRepository.averagePulseRateSince(
            userId, 
            LocalDateTime.now().minusMonths(1)
        )
        
        val pulseTypeCounts = pulseRecordRepository.countPulseTypesByUserId(userId)
        val distribution = pulseTypeCounts.associate { 
            (it[0] as String) to (it[1] as Long)
        }
        
        return PulseStatisticsResponse(
            totalRecords = totalRecords,
            avgPulseRate = avgPulseRate,
            pulseTypeDistribution = distribution,
            recentTrend = "stable"
        )
    }
    
    private fun mapToResponse(record: PulseRecord): PulseRecordResponse {
        return PulseRecordResponse(
            id = record.id,
            userId = record.userId,
            recordTime = record.recordTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            mainPulse = record.mainPulse ?: "未知",
            secondaryPulse = record.secondaryPulse,
            pulseRate = record.pulseRate ?: 0,
            syndrome = record.syndrome,
            confidence = record.syndromeConfidence
        )
    }
    
    private fun mapToDetailResponse(record: PulseRecord): PulseRecordDetailResponse {
        return PulseRecordDetailResponse(
            id = record.id,
            userId = record.userId,
            deviceId = record.deviceId,
            recordTime = record.recordTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            measurementDuration = record.measurementDuration,
            signalQuality = record.signalQuality,
            mainPulse = record.mainPulse ?: "未知",
            secondaryPulse = record.secondaryPulse,
            pulseRate = record.pulseRate ?: 0,
            pulseFeatures = PulseFeaturesDto(
                position = com.tcmpulse.dto.PositionFeaturesDto(
                    floating = record.positionFloating ?: BigDecimal.ZERO,
                    normal = record.positionNormal ?: BigDecimal.ZERO,
                    deep = record.positionDeep ?: BigDecimal.ZERO
                ),
                rate = com.tcmpulse.dto.RateFeaturesDto(
                    rateValue = record.pulseRate ?: 0,
                    category = record.rateCategory ?: "normal",
                    rhythmRegularity = record.rhythmRegularity ?: BigDecimal.ZERO,
                    intermittent = record.hasIntermittent
                ),
                force = com.tcmpulse.dto.ForceFeaturesDto(
                    forceScore = record.forceScore ?: BigDecimal.ZERO,
                    systolicAmplitude = record.systolicAmplitude ?: BigDecimal.ZERO,
                    diastolicAmplitude = record.diastolicAmplitude ?: BigDecimal.ZERO
                ),
                shape = com.tcmpulse.dto.ShapeFeaturesDto(
                    widthScore = record.widthScore ?: BigDecimal.ZERO,
                    lengthScore = record.lengthScore ?: BigDecimal.ZERO,
                    smoothness = record.smoothness ?: BigDecimal.ZERO,
                    tautness = record.tautness ?: BigDecimal.ZERO,
                    fullness = record.fullness ?: BigDecimal.ZERO,
                    hollowness = record.hollowness ?: BigDecimal.ZERO
                ),
                momentum = com.tcmpulse.dto.MomentumFeaturesDto(
                    risingSlope = record.risingSlope ?: BigDecimal.ZERO,
                    fallingSlope = record.fallingSlope ?: BigDecimal.ZERO,
                    dicroticNotchDepth = record.dicroticNotchDepth ?: BigDecimal.ZERO,
                    waveArea = record.waveArea ?: BigDecimal.ZERO
                )
            ),
            syndrome = record.syndrome,
            syndromeConfidence = record.syndromeConfidence
        )
    }
}
