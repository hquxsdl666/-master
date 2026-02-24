package com.tcmpulse.service

import com.tcmpulse.dto.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

@Service
class PulseAnalysisService {
    
    // 28种脉象列表
    private val pulseTypes = listOf(
        "浮脉", "沉脉", "迟脉", "数脉", "虚脉", "实脉",
        "滑脉", "涩脉", "弦脉", "紧脉", "洪脉", "细脉",
        "濡脉", "弱脉", "微脉", "散脉", "芤脉", "革脉",
        "牢脉", "伏脉", "缓脉", "结脉", "代脉", "促脉",
        "疾脉", "动脉", "长脉", "短脉"
    )
    
    // 证型与脉象对应关系
    private val syndromePulseMap = mapOf(
        "表证" to listOf("浮脉"),
        "里实热证" to listOf("洪脉", "数脉", "滑脉"),
        "里虚寒证" to listOf("沉脉", "迟脉", "细脉"),
        "气虚证" to listOf("虚脉", "细脉", "弱脉"),
        "血虚证" to listOf("细脉", "涩脉"),
        "阴虚证" to listOf("细数脉"),
        "阳虚证" to listOf("沉脉", "细脉", "迟脉"),
        "气滞证" to listOf("弦脉", "涩脉"),
        "血瘀证" to listOf("涩脉", "结脉", "代脉"),
        "痰湿证" to listOf("滑脉", "濡脉", "缓脉"),
        "肝郁气滞" to listOf("弦脉", "细脉"),
        "肝阳上亢" to listOf("弦脉", "细数脉"),
        "心火亢盛" to listOf("数脉", "洪脉"),
        "脾胃虚弱" to listOf("缓脉", "弱脉"),
        "肾气不足" to listOf("沉脉", "细脉", "弱脉")
    )
    
    /**
     * 分析PPG数据，识别脉象
     */
    fun analyzePulse(ppgData: DoubleArray, sampleRate: Int): PulseAnalysisResponse {
        // 1. 信号预处理
        val processedSignal = preprocessSignal(ppgData, sampleRate)
        
        // 2. 特征提取
        val features = extractFeatures(processedSignal, sampleRate)
        
        // 3. 脉象分类
        val classificationResult = classifyPulse(features)
        
        // 4. 辨证分析
        val syndromeResult = analyzeSyndrome(classificationResult)
        
        return PulseAnalysisResponse(
            mainPulse = classificationResult.mainPulse,
            mainPulseConfidence = classificationResult.mainConfidence,
            secondaryPulse = classificationResult.secondaryPulse,
            secondaryPulseConfidence = classificationResult.secondaryConfidence,
            pulseRate = features.pulseRate,
            pulseFeatures = mapToPulseFeaturesDto(features),
            syndrome = syndromeResult?.first,
            syndromeConfidence = syndromeResult?.second,
            allProbabilities = classificationResult.allProbabilities
        )
    }
    
    /**
     * 信号预处理
     */
    private fun preprocessSignal(signal: DoubleArray, sampleRate: Int): DoubleArray {
        // 去除趋势
        val detrended = removeTrend(signal)
        
        // 带通滤波 (0.5-8Hz)
        val filtered = bandpassFilter(detrended, 0.5, 8.0, sampleRate)
        
        // 归一化
        return normalize(filtered)
    }
    
    /**
     * 特征提取
     */
    private fun extractFeatures(signal: DoubleArray, sampleRate: Int): PulseFeatures {
        // 峰值检测
        val peaks = detectPeaks(signal, sampleRate)
        
        // 计算脉率
        val pulseRate = if (peaks.size >= 2) {
            val avgInterval = (peaks.last() - peaks.first()).toDouble() / (peaks.size - 1) / sampleRate
            (60.0 / avgInterval).toInt()
        } else 72
        
        // 脉率分类
        val rateCategory = when {
            pulseRate < 60 -> "slow"
            pulseRate > 90 -> "fast"
            else -> "normal"
        }
        
        // 计算各种特征
        val mean = signal.average()
        val variance = signal.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance)
        
        // 脉力得分 (基于信号幅度)
        val maxAmp = signal.maxOrNull() ?: 0.0
        val minAmp = signal.minOrNull() ?: 0.0
        val forceScore = ((maxAmp - minAmp) / 2.0).coerceIn(0.0, 1.0)
        
        // 脉形特征
        val widthScore = calculatePulseWidth(signal, peaks)
        val smoothness = calculateSmoothness(signal)
        val tautness = calculateTautness(signal, peaks)
        
        // 脉位特征 (模拟)
        val positionFloating = if (forceScore > 0.6) BigDecimal("0.7") else BigDecimal("0.2")
        val positionNormal = BigDecimal("0.5")
        val positionDeep = if (forceScore < 0.4) BigDecimal("0.7") else BigDecimal("0.2")
        
        return PulseFeatures(
            pulseRate = pulseRate,
            rateCategory = rateCategory,
            rhythmRegularity = calculateRhythmRegularity(peaks, sampleRate),
            intermittent = false,
            forceScore = BigDecimal(forceScore).setScale(2, RoundingMode.HALF_UP),
            systolicAmplitude = BigDecimal(maxAmp).setScale(3, RoundingMode.HALF_UP),
            diastolicAmplitude = BigDecimal(minAmp).setScale(3, RoundingMode.HALF_UP),
            widthScore = BigDecimal(widthScore).setScale(2, RoundingMode.HALF_UP),
            lengthScore = BigDecimal("0.5"),
            smoothness = BigDecimal(smoothness).setScale(2, RoundingMode.HALF_UP),
            tautness = BigDecimal(tautness).setScale(2, RoundingMode.HALF_UP),
            fullness = BigDecimal(forceScore * 0.8).setScale(2, RoundingMode.HALF_UP),
            hollowness = BigDecimal("0.1"),
            risingSlope = BigDecimal("0.5"),
            fallingSlope = BigDecimal("0.4"),
            dicroticNotchDepth = BigDecimal("0.3"),
            waveArea = BigDecimal(signal.sum()).setScale(3, RoundingMode.HALF_UP),
            dominantFreq = BigDecimal(pulseRate.toDouble() / 60.0).setScale(2, RoundingMode.HALF_UP),
            positionFloating = positionFloating,
            positionNormal = positionNormal,
            positionDeep = positionDeep
        )
    }
    
    /**
     * 脉象分类
     */
    private fun classifyPulse(features: PulseFeatures): ClassificationResult {
        // 基于特征计算各脉象的概率
        val probabilities = mutableMapOf<String, Double>()
        
        // 浮脉类
        probabilities["浮脉"] = features.positionFloating.toDouble() * 0.8
        probabilities["洪脉"] = if (features.forceScore.toDouble() > 0.7) features.positionFloating.toDouble() * 0.9 else 0.1
        probabilities["濡脉"] = if (features.forceScore.toDouble() < 0.3) features.positionFloating.toDouble() * 0.7 else 0.05
        
        // 沉脉类
        probabilities["沉脉"] = features.positionDeep.toDouble() * 0.8
        probabilities["弱脉"] = if (features.forceScore.toDouble() < 0.3) features.positionDeep.toDouble() * 0.7 else 0.05
        
        // 迟数脉类
        probabilities["迟脉"] = if (features.pulseRate < 60) 0.9 else 0.05
        probabilities["数脉"] = if (features.pulseRate > 90) 0.9 else 0.05
        probabilities["缓脉"] = if (features.pulseRate in 60..70) 0.8 else 0.1
        probabilities["疾脉"] = if (features.pulseRate > 120) 0.95 else 0.02
        
        // 虚脉类
        probabilities["虚脉"] = if (features.forceScore.toDouble() < 0.3) 0.85 else 0.1
        probabilities["细脉"] = if (features.widthScore.toDouble() < 0.3) 0.8 else 0.15
        probabilities["微脉"] = if (features.forceScore.toDouble() < 0.2) 0.75 else 0.05
        
        // 实脉类
        probabilities["实脉"] = if (features.forceScore.toDouble() > 0.7) 0.85 else 0.1
        probabilities["滑脉"] = if (features.smoothness.toDouble() > 0.7) 0.8 else 0.1
        probabilities["弦脉"] = if (features.tautness.toDouble() > 0.6) 0.85 else 0.15
        probabilities["紧脉"] = if (features.tautness.toDouble() > 0.8) 0.8 else 0.1
        probabilities["洪脉"] = if (features.forceScore.toDouble() > 0.7 && features.widthScore.toDouble() > 0.6) 0.85 else 0.1
        
        // 归一化概率
        val totalProb = probabilities.values.sum()
        val normalizedProbs = probabilities.mapValues { it.value / totalProb }
        
        // 排序获取最高概率的脉象
        val sortedProbs = normalizedProbs.entries.sortedByDescending { it.value }
        
        val mainPulse = sortedProbs.getOrNull(0)?.key ?: "平和脉"
        val mainConfidence = BigDecimal(sortedProbs.getOrNull(0)?.value ?: 0.5).setScale(2, RoundingMode.HALF_UP)
        
        val secondaryPulse = sortedProbs.getOrNull(1)?.key
        val secondaryConfidence = sortedProbs.getOrNull(1)?.value?.let {
            BigDecimal(it).setScale(2, RoundingMode.HALF_UP)
        }
        
        val allProbabilities = sortedProbs.map { 
            PulseProbabilityDto(
                pulse = it.key,
                probability = BigDecimal(it.value).setScale(2, RoundingMode.HALF_UP)
            )
        }
        
        return ClassificationResult(
            mainPulse = mainPulse,
            mainConfidence = mainConfidence,
            secondaryPulse = secondaryPulse,
            secondaryConfidence = secondaryConfidence,
            allProbabilities = allProbabilities
        )
    }
    
    /**
     * 辨证分析
     */
    private fun analyzeSyndrome(classification: ClassificationResult): Pair<String, BigDecimal>? {
        val pulses = listOfNotNull(classification.mainPulse, classification.secondaryPulse)
        
        // 查找匹配的证型
        val matchedSyndromes = syndromePulseMap.filter { (_, pulseList) ->
            pulses.any { pulse -> pulseList.contains(pulse) }
        }
        
        return if (matchedSyndromes.isNotEmpty()) {
            val bestMatch = matchedSyndromes.keys.first()
            val confidence = classification.mainConfidence.multiply(BigDecimal("0.9"))
            Pair(bestMatch, confidence)
        } else null
    }
    
    // 辅助方法
    private fun removeTrend(signal: DoubleArray): DoubleArray {
        val windowSize = minOf(100, signal.size / 10)
        val result = DoubleArray(signal.size)
        
        for (i in signal.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size, i + windowSize / 2)
            val trend = signal.sliceArray(start until end).average()
            result[i] = signal[i] - trend
        }
        
        return result
    }
    
    private fun bandpassFilter(signal: DoubleArray, lowFreq: Double, highFreq: Double, sampleRate: Int): DoubleArray {
        // 简化的带通滤波
        return signal
    }
    
    private fun normalize(signal: DoubleArray): DoubleArray {
        val mean = signal.average()
        val std = sqrt(signal.map { (it - mean) * (it - mean) }.average())
        return if (std > 0) signal.map { (it - mean) / std }.toDoubleArray() else signal
    }
    
    private fun detectPeaks(signal: DoubleArray, sampleRate: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        val minDistance = sampleRate / 2 // 最小峰值间距 (0.5秒)
        
        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] && signal[i] > signal[i + 1]) {
                if (signal[i] > 0.5) { // 阈值
                    if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                        peaks.add(i)
                    }
                }
            }
        }
        
        return peaks
    }
    
    private fun calculatePulseWidth(signal: DoubleArray, peaks: List<Int>): Double {
        return if (peaks.size >= 2) {
            val avgWidth = peaks.zipWithNext { a, b -> b - a }.average()
            (avgWidth / signal.size).coerceIn(0.0, 1.0)
        } else 0.5
    }
    
    private fun calculateSmoothness(signal: DoubleArray): Double {
        val diff = signal.zipWithNext { a, b -> kotlin.math.abs(b - a) }.average()
        return (1.0 - diff).coerceIn(0.0, 1.0)
    }
    
    private fun calculateTautness(signal: DoubleArray, peaks: List<Int>): Double {
        return if (peaks.size >= 2) {
            val risingSlopes = peaks.map { peak ->
                if (peak > 10) {
                    val start = maxOf(0, peak - 10)
                    (signal[peak] - signal[start]) / 10.0
                } else 0.0
            }.average()
            risingSlopes.coerceIn(0.0, 1.0)
        } else 0.5
    }
    
    private fun calculateRhythmRegularity(peaks: List<Int>, sampleRate: Int): BigDecimal {
        return if (peaks.size >= 3) {
            val intervals = peaks.zipWithNext { a, b -> b - a }
            val meanInterval = intervals.average()
            val variance = intervals.map { (it - meanInterval) * (it - meanInterval) }.average()
            val cv = sqrt(variance) / meanInterval
            BigDecimal((1.0 - cv).coerceIn(0.0, 1.0)).setScale(2, RoundingMode.HALF_UP)
        } else BigDecimal("0.9")
    }
    
    private fun mapToPulseFeaturesDto(features: PulseFeatures): PulseFeaturesDto {
        return PulseFeaturesDto(
            position = PositionFeaturesDto(
                floating = features.positionFloating,
                normal = features.positionNormal,
                deep = features.positionDeep
            ),
            rate = RateFeaturesDto(
                rateValue = features.pulseRate,
                category = features.rateCategory,
                rhythmRegularity = features.rhythmRegularity,
                intermittent = features.intermittent
            ),
            force = ForceFeaturesDto(
                forceScore = features.forceScore,
                systolicAmplitude = features.systolicAmplitude,
                diastolicAmplitude = features.diastolicAmplitude
            ),
            shape = ShapeFeaturesDto(
                widthScore = features.widthScore,
                lengthScore = features.lengthScore,
                smoothness = features.smoothness,
                tautness = features.tautness,
                fullness = features.fullness,
                hollowness = features.hollowness
            ),
            momentum = MomentumFeaturesDto(
                risingSlope = features.risingSlope,
                fallingSlope = features.fallingSlope,
                dicroticNotchDepth = features.dicroticNotchDepth,
                waveArea = features.waveArea
            )
        )
    }
}

// 数据类
data class PulseFeatures(
    val pulseRate: Int,
    val rateCategory: String,
    val rhythmRegularity: BigDecimal,
    val intermittent: Boolean,
    val forceScore: BigDecimal,
    val systolicAmplitude: BigDecimal,
    val diastolicAmplitude: BigDecimal,
    val widthScore: BigDecimal,
    val lengthScore: BigDecimal,
    val smoothness: BigDecimal,
    val tautness: BigDecimal,
    val fullness: BigDecimal,
    val hollowness: BigDecimal,
    val risingSlope: BigDecimal,
    val fallingSlope: BigDecimal,
    val dicroticNotchDepth: BigDecimal,
    val waveArea: BigDecimal,
    val dominantFreq: BigDecimal,
    val positionFloating: BigDecimal,
    val positionNormal: BigDecimal,
    val positionDeep: BigDecimal
)

data class ClassificationResult(
    val mainPulse: String,
    val mainConfidence: BigDecimal,
    val secondaryPulse: String?,
    val secondaryConfidence: BigDecimal?,
    val allProbabilities: List<PulseProbabilityDto>
)
