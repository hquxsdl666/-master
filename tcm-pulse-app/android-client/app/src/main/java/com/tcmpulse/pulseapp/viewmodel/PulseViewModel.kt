package com.tcmpulse.pulseapp.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmpulse.pulseapp.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PulseViewModel @Inject constructor() : ViewModel() {
    
    // 采集状态
    private val _collectionState = mutableStateOf<PulseCollectionState>(PulseCollectionState.Idle)
    val collectionState: State<PulseCollectionState> = _collectionState
    
    // 波形数据
    private val _waveformData = mutableStateOf<List<Float>>(emptyList())
    val waveformData: State<List<Float>> = _waveformData
    
    // 最近记录
    private val _recentRecords = mutableStateOf<List<PulseRecord>>(emptyList())
    val recentRecords: State<List<PulseRecord>> = _recentRecords
    
    // 健康评分
    private val _healthScore = mutableStateOf(85)
    val healthScore: State<Int> = _healthScore
    
    // 方剂推荐
    private val _recommendations = mutableStateOf<List<PrescriptionRecommendation>>(emptyList())
    val recommendations: State<List<PrescriptionRecommendation>> = _recommendations
    
    // 模拟数据
    private val mockRecords = listOf(
        PulseRecord(
            id = "1",
            userId = "user1",
            deviceId = "device1",
            recordTime = java.time.LocalDateTime.now().minusHours(2),
            mainPulse = "平和脉",
            secondaryPulse = null,
            pulseRate = 72,
            syndrome = null,
            confidence = null,
            signalQuality = java.math.BigDecimal("0.92")
        ),
        PulseRecord(
            id = "2",
            userId = "user1",
            deviceId = "device1",
            recordTime = java.time.LocalDateTime.now().minusDays(1),
            mainPulse = "弦脉",
            secondaryPulse = "细脉",
            pulseRate = 76,
            syndrome = "肝郁气滞",
            confidence = java.math.BigDecimal("0.85"),
            signalQuality = java.math.BigDecimal("0.88")
        )
    )
    
    init {
        _recentRecords.value = mockRecords
    }
    
    /**
     * 开始脉象采集
     */
    fun startPulseCollection() {
        viewModelScope.launch {
            _collectionState.value = PulseCollectionState.Connecting
            delay(1000)
            
            _collectionState.value = PulseCollectionState.Collecting
            
            // 模拟采集过程
            var progress = 0
            while (progress < 100) {
                delay(600)
                progress += 5
                
                // 更新波形数据
                _waveformData.value = generateMockWaveform()
                
                _collectionState.value = PulseCollectionState.Progress(
                    percent = progress,
                    quality = 0.7f + kotlin.random.Random.nextFloat() * 0.25f
                )
            }
            
            // 分析中
            _collectionState.value = PulseCollectionState.Analyzing
            delay(1500)
            
            // 分析完成
            val result = PulseAnalysisResponse(
                mainPulse = "弦脉",
                mainPulseConfidence = java.math.BigDecimal("0.87"),
                secondaryPulse = "细脉",
                secondaryPulseConfidence = java.math.BigDecimal("0.65"),
                pulseRate = 74,
                pulseFeatures = createMockPulseFeatures(),
                syndrome = "肝郁气滞",
                syndromeConfidence = java.math.BigDecimal("0.82"),
                allProbabilities = emptyList()
            )
            
            _collectionState.value = PulseCollectionState.Success(result)
        }
    }
    
    /**
     * 取消采集
     */
    fun cancelCollection() {
        _collectionState.value = PulseCollectionState.Idle
        _waveformData.value = emptyList()
    }
    
    /**
     * 获取记录详情
     */
    fun getRecordDetail(recordId: String?): PulseRecordDetail? {
        if (recordId == null) return null
        
        // 模拟返回详情
        return PulseRecordDetail(
            id = recordId,
            userId = "user1",
            deviceId = "device1",
            recordTime = java.time.LocalDateTime.now().minusHours(2),
            measurementDuration = 60,
            signalQuality = java.math.BigDecimal("0.92"),
            mainPulse = "弦脉",
            secondaryPulse = "细脉",
            pulseRate = 74,
            pulseFeatures = createMockPulseFeatures(),
            syndrome = "肝郁气滞",
            syndromeConfidence = java.math.BigDecimal("0.82")
        )
    }
    
    /**
     * 分享报告
     */
    fun shareReport(recordId: String?) {
        // 实现分享功能
    }
    
    /**
     * 生成模拟波形数据
     */
    private fun generateMockWaveform(): List<Float> {
        val pi = Math.PI.toFloat()
        return List(200) { index ->
            val t = index / 50.0f
            kotlin.math.sin(t * 2f * pi * 1.2f) * 0.5f +
            kotlin.math.sin(t * 2f * pi * 2.4f) * 0.25f +
            (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f
        }
    }
    
    /**
     * 创建模拟脉象特征
     */
    private fun createMockPulseFeatures(): PulseFeatures {
        return PulseFeatures(
            position = PositionFeatures(
                floating = java.math.BigDecimal("0.25"),
                normal = java.math.BigDecimal("0.65"),
                deep = java.math.BigDecimal("0.10")
            ),
            rate = RateFeatures(
                rateValue = 74,
                category = "normal",
                rhythmRegularity = java.math.BigDecimal("0.95"),
                intermittent = false
            ),
            force = ForceFeatures(
                forceScore = java.math.BigDecimal("0.55"),
                systolicAmplitude = java.math.BigDecimal("1.25"),
                diastolicAmplitude = java.math.BigDecimal("0.35")
            ),
            shape = ShapeFeatures(
                widthScore = java.math.BigDecimal("0.45"),
                lengthScore = java.math.BigDecimal("0.60"),
                smoothness = java.math.BigDecimal("0.70"),
                tautness = java.math.BigDecimal("0.75"),
                fullness = java.math.BigDecimal("0.50"),
                hollowness = java.math.BigDecimal("0.15")
            ),
            momentum = MomentumFeatures(
                risingSlope = java.math.BigDecimal("0.65"),
                fallingSlope = java.math.BigDecimal("0.45"),
                dicroticNotchDepth = java.math.BigDecimal("0.30"),
                waveArea = java.math.BigDecimal("125.5")
            )
        )
    }
}
