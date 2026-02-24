package com.tcmpulse.pulseapp.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmpulse.pulseapp.bluetooth.WatchBluetoothManager
import com.tcmpulse.pulseapp.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PulseViewModel @Inject constructor(
    private val watchManager: WatchBluetoothManager
) : ViewModel() {

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

    private var scanJob: Job? = null
    private var collectJob: Job? = null
    private val heartRateSamples = mutableListOf<Int>()

    // ---- Mock 数据 ----

    private val mockRecommendations = listOf(
        PrescriptionRecommendation(
            prescriptionId = 1L, name = "逍遥散",
            composition = listOf(
                HerbDosage("柴胡", "10g", "君药"), HerbDosage("白芍", "10g", "臣药"),
                HerbDosage("当归", "10g", "臣药"), HerbDosage("茯苓", "15g", "佐药"),
                HerbDosage("白术", "10g", "佐药"), HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "疏肝解郁，养血调经。用于肝郁血虚，两胁作痛，头痛目眩，神疲食少。",
            matchScore = java.math.BigDecimal("0.92"), rank = 1
        ),
        PrescriptionRecommendation(
            prescriptionId = 2L, name = "柴胡疏肝散",
            composition = listOf(
                HerbDosage("柴胡", "12g", "君药"), HerbDosage("香附", "10g", "臣药"),
                HerbDosage("川芎", "9g", "臣药"),  HerbDosage("枳壳", "9g", "佐药"),
                HerbDosage("陈皮", "9g", "佐药"),  HerbDosage("芍药", "9g", "佐药"),
                HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "疏肝解郁，行气止痛。用于肝气郁滞，胁肋疼痛，情志抑郁易怒。",
            matchScore = java.math.BigDecimal("0.85"), rank = 2
        ),
        PrescriptionRecommendation(
            prescriptionId = 3L, name = "四君子汤",
            composition = listOf(
                HerbDosage("人参", "9g", "君药"), HerbDosage("白术", "9g", "臣药"),
                HerbDosage("茯苓", "9g", "佐药"), HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "益气健脾。用于脾胃气虚，面色萎白，气短乏力，食少便溏。",
            matchScore = java.math.BigDecimal("0.71"), rank = 3
        )
    )

    private val mockRecords = listOf(
        PulseRecord(
            id = "1", userId = "user1", deviceId = "device1",
            recordTime = java.time.LocalDateTime.now().minusHours(2),
            mainPulse = "平和脉", secondaryPulse = null, pulseRate = 72,
            syndrome = null, confidence = null, signalQuality = java.math.BigDecimal("0.92")
        ),
        PulseRecord(
            id = "2", userId = "user1", deviceId = "device1",
            recordTime = java.time.LocalDateTime.now().minusDays(1),
            mainPulse = "弦脉", secondaryPulse = "细脉", pulseRate = 76,
            syndrome = "肝郁气滞", confidence = java.math.BigDecimal("0.85"),
            signalQuality = java.math.BigDecimal("0.88")
        )
    )

    init {
        _recentRecords.value = mockRecords
        _recommendations.value = mockRecommendations
    }

    // ---- BLE 心率广播流程 ----

    /**
     * 第一步：开始 BLE 扫描，等待手表心率广播信号
     */
    fun startPulseCollection() {
        heartRateSamples.clear()
        _waveformData.value = emptyList()
        _collectionState.value = PulseCollectionState.Scanning()

        watchManager.startScan()

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            // 监听心率数据，第一次收到时自动开始60秒采集
            launch {
                watchManager.heartRate.collect { hr ->
                    if (hr > 0 && _collectionState.value is PulseCollectionState.Scanning) {
                        val deviceName = watchManager.sourceDevice.value
                        _collectionState.value = PulseCollectionState.Scanning(deviceName)
                        startCollection()
                    }
                }
            }
            // 监听错误
            launch {
                watchManager.error.collect { msg ->
                    if (msg.isNotEmpty() && _collectionState.value is PulseCollectionState.Scanning) {
                        _collectionState.value = PulseCollectionState.Error(msg)
                    }
                }
            }
            // 超时：60秒内未收到广播则提示
            delay(60_000L)
            if (_collectionState.value is PulseCollectionState.Scanning) {
                _collectionState.value = PulseCollectionState.Error(
                    "60秒内未检测到心率广播\n" +
                    "请在手表上开启「心率广播」功能：\n" +
                    "设置 → 健康监测 → 心率广播 → 开启"
                )
                watchManager.stopScan()
            }
        }
    }

    /**
     * 第二步：检测到广播信号后，采集60秒数据
     */
    private fun startCollection() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            val totalSteps = 20  // 每步3秒，共60秒
            for (step in 1..totalSteps) {
                delay(3_000L)

                val hr = watchManager.heartRate.value
                if (hr in 30..220) heartRateSamples.add(hr)

                _waveformData.value = generateWaveform(hr)
                val quality = when {
                    hr in 50..100 -> 0.90f
                    hr in 30..130 -> 0.70f
                    else          -> 0.45f
                }
                _collectionState.value = PulseCollectionState.Progress(
                    percent = step * 100 / totalSteps,
                    quality = quality,
                    bpm     = hr
                )
            }

            // 分析阶段
            _collectionState.value = PulseCollectionState.Analyzing
            watchManager.stopScan()
            delay(1_500L)
            analyzeAndComplete()
        }
    }

    /**
     * 取消采集
     */
    fun cancelCollection() {
        scanJob?.cancel()
        collectJob?.cancel()
        watchManager.stopScan()
        _collectionState.value = PulseCollectionState.Idle
        _waveformData.value = emptyList()
    }

    // ---- 分析 ----

    private fun analyzeAndComplete() {
        val avgHr = if (heartRateSamples.isNotEmpty())
            heartRateSamples.average().toInt()
        else
            watchManager.heartRate.value.takeIf { it > 0 } ?: 72

        val (mainPulse, conf, syndrome) = classifyPulse(avgHr)
        val result = PulseAnalysisResponse(
            mainPulse                = mainPulse,
            mainPulseConfidence      = java.math.BigDecimal(conf.toString()),
            secondaryPulse           = null,
            secondaryPulseConfidence = null,
            pulseRate                = avgHr,
            pulseFeatures            = buildPulseFeatures(avgHr),
            syndrome                 = syndrome,
            syndromeConfidence       = java.math.BigDecimal("0.80"),
            allProbabilities         = emptyList()
        )
        _collectionState.value = PulseCollectionState.Success(result)
    }

    private fun classifyPulse(hr: Int): Triple<String, Double, String?> = when {
        hr < 60  -> Triple("迟脉", 0.82, "阳虚寒凝")
        hr <= 90 -> Triple("平和脉", 0.88, null)
        hr <= 110 -> Triple("数脉", 0.84, "阴虚内热")
        else     -> Triple("疾脉", 0.79, "阳热亢盛")
    }

    // ---- 记录详情 ----

    fun getRecordDetail(recordId: String?): PulseRecordDetail? {
        if (recordId == null) return null
        val base = mockRecords.find { it.id == recordId }
        return PulseRecordDetail(
            id = recordId, userId = "user1", deviceId = "device1",
            recordTime = base?.recordTime ?: java.time.LocalDateTime.now().minusHours(2),
            measurementDuration = 60,
            signalQuality = base?.signalQuality ?: java.math.BigDecimal("0.92"),
            mainPulse = base?.mainPulse ?: "平和脉",
            secondaryPulse = base?.secondaryPulse,
            pulseRate = base?.pulseRate ?: 72,
            pulseFeatures = buildPulseFeatures(base?.pulseRate ?: 72),
            syndrome = base?.syndrome,
            syndromeConfidence = base?.confidence
        )
    }

    fun shareReport(recordId: String?) {}

    // ---- 辅助 ----

    private fun generateWaveform(hr: Int): List<Float> {
        val pi = Math.PI.toFloat()
        val freq = if (hr > 0) hr / 60.0f else 1.2f
        return List(200) { i ->
            val t = i / 50.0f
            val primary = kotlin.math.sin(t * 2f * pi * freq)
            val dicrotic = kotlin.math.sin(t * 2f * pi * freq * 2f) * 0.15f
            val noise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.06f
            (if (primary > 0) primary * 0.6f else primary * 0.25f) + dicrotic + noise
        }
    }

    private fun buildPulseFeatures(hr: Int) = PulseFeatures(
        position = PositionFeatures(
            floating = java.math.BigDecimal("0.25"),
            normal   = java.math.BigDecimal("0.65"),
            deep     = java.math.BigDecimal("0.10")
        ),
        rate = RateFeatures(
            rateValue = hr, category = when { hr < 60 -> "slow"; hr <= 90 -> "normal"; else -> "fast" },
            rhythmRegularity = java.math.BigDecimal("0.95"), intermittent = false
        ),
        force = ForceFeatures(
            forceScore = java.math.BigDecimal("0.55"),
            systolicAmplitude = java.math.BigDecimal("1.25"),
            diastolicAmplitude = java.math.BigDecimal("0.35")
        ),
        shape = ShapeFeatures(
            widthScore = java.math.BigDecimal("0.45"), lengthScore = java.math.BigDecimal("0.60"),
            smoothness = java.math.BigDecimal("0.70"), tautness = java.math.BigDecimal("0.75"),
            fullness = java.math.BigDecimal("0.50"),   hollowness = java.math.BigDecimal("0.15")
        ),
        momentum = MomentumFeatures(
            risingSlope = java.math.BigDecimal("0.65"), fallingSlope = java.math.BigDecimal("0.45"),
            dicroticNotchDepth = java.math.BigDecimal("0.30"), waveArea = java.math.BigDecimal("125.5")
        )
    )
}
