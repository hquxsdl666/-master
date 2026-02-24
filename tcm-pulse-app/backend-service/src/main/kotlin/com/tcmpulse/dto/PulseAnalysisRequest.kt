package com.tcmpulse.dto

import java.math.BigDecimal

data class PulseAnalysisRequest(
    val ppgData: String, // Base64编码的PPG数据
    val sampleRate: Int = 100,
    val duration: Int = 60
)

data class PulseAnalysisResponse(
    val mainPulse: String,
    val mainPulseConfidence: BigDecimal,
    val secondaryPulse: String?,
    val secondaryPulseConfidence: BigDecimal?,
    val pulseRate: Int,
    val pulseFeatures: PulseFeaturesDto,
    val syndrome: String?,
    val syndromeConfidence: BigDecimal?,
    val allProbabilities: List<PulseProbabilityDto>
)

data class PulseFeaturesDto(
    val position: PositionFeaturesDto,
    val rate: RateFeaturesDto,
    val force: ForceFeaturesDto,
    val shape: ShapeFeaturesDto,
    val momentum: MomentumFeaturesDto
)

data class PositionFeaturesDto(
    val floating: BigDecimal,
    val normal: BigDecimal,
    val deep: BigDecimal
)

data class RateFeaturesDto(
    val rateValue: Int,
    val category: String,
    val rhythmRegularity: BigDecimal,
    val intermittent: Boolean
)

data class ForceFeaturesDto(
    val forceScore: BigDecimal,
    val systolicAmplitude: BigDecimal,
    val diastolicAmplitude: BigDecimal
)

data class ShapeFeaturesDto(
    val widthScore: BigDecimal,
    val lengthScore: BigDecimal,
    val smoothness: BigDecimal,
    val tautness: BigDecimal,
    val fullness: BigDecimal,
    val hollowness: BigDecimal
)

data class MomentumFeaturesDto(
    val risingSlope: BigDecimal,
    val fallingSlope: BigDecimal,
    val dicroticNotchDepth: BigDecimal,
    val waveArea: BigDecimal
)

data class PulseProbabilityDto(
    val pulse: String,
    val probability: BigDecimal
)
