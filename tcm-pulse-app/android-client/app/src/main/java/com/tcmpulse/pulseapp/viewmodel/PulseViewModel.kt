package com.tcmpulse.pulseapp.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmpulse.pulseapp.bluetooth.WatchBluetoothManager
import com.tcmpulse.pulseapp.bluetooth.WatchConnectionState
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

    // BLE 连接状态监听 Job
    private var bleObserveJob: Job? = null
    // 采集过程 Job
    private var collectionJob: Job? = null
    // 采集期间收集到的心率序列
    private val heartRateSamples = mutableListOf<Int>()

    // 模拟方剂数据
    private val mockRecommendations = listOf(
        PrescriptionRecommendation(
            prescriptionId = 1L,
            name = "逍遥散",
            composition = listOf(
                HerbDosage("柴胡", "10g", "君药"),
                HerbDosage("白芍", "10g", "臣药"),
                HerbDosage("当归", "10g", "臣药"),
                HerbDosage("茯苓", "15g", "佐药"),
                HerbDosage("白术", "10g", "佐药"),
                HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "疏肝解郁，养血调经。用于肝郁血虚，两胁作痛，头痛目眩，神疲食少。",
            matchScore = java.math.BigDecimal("0.92"),
            rank = 1
        ),
        PrescriptionRecommendation(
            prescriptionId = 2L,
            name = "柴胡疏肝散",
            composition = listOf(
                HerbDosage("柴胡", "12g", "君药"),
                HerbDosage("香附", "10g", "臣药"),
                HerbDosage("川芎", "9g", "臣药"),
                HerbDosage("枳壳", "9g", "佐药"),
                HerbDosage("陈皮", "9g", "佐药"),
                HerbDosage("芍药", "9g", "佐药"),
                HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "疏肝解郁，行气止痛。用于肝气郁滞，胁肋疼痛，情志抑郁易怒。",
            matchScore = java.math.BigDecimal("0.85"),
            rank = 2
        ),
        PrescriptionRecommendation(
            prescriptionId = 3L,
            name = "四君子汤",
            composition = listOf(
                HerbDosage("人参", "9g", "君药"),
                HerbDosage("白术", "9g", "臣药"),
                HerbDosage("茯苓", "9g", "佐药"),
                HerbDosage("炙甘草", "6g", "使药")
            ),
            efficacy = "益气健脾。用于脾胃气虚，面色萎白，气短乏力，食少便溏。",
            matchScore = java.math.BigDecimal("0.71"),
            rank = 3
        )
    )

    // 模拟历史脉诊记录
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
        _recommendations.value = mockRecommendations
    }

    // ---- 蓝牙流程 ----

    /**
     * 第一步：开始 BLE 扫描
     *
     * - 广播模式：手表开启「心率广播」后，收到广播数据自动开始采集，无需用户选择设备
     * - GATT 备用：如果扫到需要 GATT 连接的设备，显示设备列表供用户选择
     */
    fun startPulseCollection() {
        heartRateSamples.clear()
        _waveformData.value = emptyList()

        _collectionState.value = PulseCollectionState.DeviceScan(emptyList())
        watchManager.startScan()

        bleObserveJob?.cancel()
        bleObserveJob = viewModelScope.launch {
            // 并行监听两个 Flow

            // ① 监听 GATT 设备列表更新
            launch {
                watchManager.scannedDevices.collect { devices ->
                    val current = _collectionState.value
                    if (current is PulseCollectionState.DeviceScan) {
                        _collectionState.value = PulseCollectionState.DeviceScan(devices)
                    }
                }
            }

            // ② 监听连接/广播状态
            launch {
                watchManager.connectionState.collect { state ->
                    when (state) {
                        // 广播模式：扫描阶段收到心率广播，自动开始采集
                        is WatchConnectionState.Measuring -> {
                            if (_collectionState.value is PulseCollectionState.DeviceScan) {
                                startRealCollection()
                            }
                        }
                        is WatchConnectionState.Error -> {
                            _collectionState.value = PulseCollectionState.Error(state.message)
                        }
                        is WatchConnectionState.Disconnected -> {
                            val cur = _collectionState.value
                            if (cur is PulseCollectionState.Collecting || cur is PulseCollectionState.Progress) {
                                _collectionState.value = PulseCollectionState.Error("手表连接已断开，请重新采集")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * 第二步（GATT 备用路径）：用户从列表选择设备后发起 GATT 连接
     */
    fun connectToWatch(address: String) {
        // 先取消扫描阶段的监听 Job，建立专用的连接监听
        bleObserveJob?.cancel()
        _collectionState.value = PulseCollectionState.Connecting

        watchManager.connectToDevice(address)

        bleObserveJob = viewModelScope.launch {
            watchManager.connectionState.collect { state ->
                when (state) {
                    is WatchConnectionState.Measuring -> {
                        if (_collectionState.value is PulseCollectionState.Connecting) {
                            startRealCollection()
                        }
                    }
                    is WatchConnectionState.Error -> {
                        _collectionState.value = PulseCollectionState.Error(state.message)
                    }
                    is WatchConnectionState.Disconnected -> {
                        val cur = _collectionState.value
                        if (cur is PulseCollectionState.Collecting || cur is PulseCollectionState.Progress) {
                            _collectionState.value = PulseCollectionState.Error("手表连接已断开，请重新采集")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * 第三步：已连接，开始60秒采集
     */
    private fun startRealCollection() {
        collectionJob?.cancel()
        heartRateSamples.clear()
        _collectionState.value = PulseCollectionState.Collecting

        collectionJob = viewModelScope.launch {
            val totalSteps = 20   // 每步3秒，共60秒
            for (step in 1..totalSteps) {
                delay(3_000L)

                val hr = watchManager.currentHeartRate.value
                if (hr in 30..250) {
                    heartRateSamples.add(hr)
                }

                // 更新波形
                _waveformData.value = generateWaveformFromHeartRate(hr)

                val quality = if (hr in 50..120) 0.85f else if (hr in 30..250) 0.60f else 0.30f
                _collectionState.value = PulseCollectionState.Progress(
                    percent = step * 100 / totalSteps,
                    quality = quality
                )
            }

            // 分析阶段
            _collectionState.value = PulseCollectionState.Analyzing
            delay(1_500L)
            analyzeAndComplete()
        }
    }

    /**
     * 取消采集
     */
    fun cancelCollection() {
        bleObserveJob?.cancel()
        collectionJob?.cancel()
        watchManager.disconnect()
        _collectionState.value = PulseCollectionState.Idle
        _waveformData.value = emptyList()
    }

    /**
     * 基于真实心率样本生成分析结果
     */
    private fun analyzeAndComplete() {
        val avgHr = if (heartRateSamples.isNotEmpty())
            heartRateSamples.average().toInt()
        else
            watchManager.currentHeartRate.value.takeIf { it > 0 } ?: 72

        val (mainPulse, mainConf, syndrome) = classifyPulse(avgHr)

        val result = PulseAnalysisResponse(
            mainPulse = mainPulse,
            mainPulseConfidence = java.math.BigDecimal(mainConf.toString()),
            secondaryPulse = null,
            secondaryPulseConfidence = null,
            pulseRate = avgHr,
            pulseFeatures = createPulseFeaturesFromHR(avgHr),
            syndrome = syndrome,
            syndromeConfidence = java.math.BigDecimal("0.80"),
            allProbabilities = emptyList()
        )

        _collectionState.value = PulseCollectionState.Success(result)
        watchManager.disconnect()
    }

    /** 简单规则分类（后期可替换为 ML 模型调用） */
    private fun classifyPulse(hr: Int): Triple<String, Double, String?> = when {
        hr < 60  -> Triple("迟脉", 0.82, "阳虚寒凝")
        hr <= 90 -> Triple("平和脉", 0.88, null)
        hr <= 110 -> Triple("数脉", 0.84, "阴虚内热")
        else     -> Triple("疾脉", 0.79, "阳热亢盛")
    }

    /**
     * 获取记录详情
     */
    fun getRecordDetail(recordId: String?): PulseRecordDetail? {
        if (recordId == null) return null
        val base = mockRecords.find { it.id == recordId }
        return PulseRecordDetail(
            id = recordId,
            userId = "user1",
            deviceId = "device1",
            recordTime = base?.recordTime ?: java.time.LocalDateTime.now().minusHours(2),
            measurementDuration = 60,
            signalQuality = base?.signalQuality ?: java.math.BigDecimal("0.92"),
            mainPulse = base?.mainPulse ?: "平和脉",
            secondaryPulse = base?.secondaryPulse,
            pulseRate = base?.pulseRate ?: 72,
            pulseFeatures = createPulseFeaturesFromHR(base?.pulseRate ?: 72),
            syndrome = base?.syndrome,
            syndromeConfidence = base?.confidence
        )
    }

    /**
     * 分享报告（占位）
     */
    fun shareReport(recordId: String?) {}

    // ---- 辅助方法 ----

    /** 根据实时心率生成类脉冲波形 */
    private fun generateWaveformFromHeartRate(hr: Int): List<Float> {
        val pi = Math.PI.toFloat()
        val freq = if (hr > 0) hr / 60.0f else 1.2f
        return List(200) { index ->
            val t = index / 50.0f
            val primary = kotlin.math.sin(t * 2f * pi * freq)
            val dicrotic = kotlin.math.sin(t * 2f * pi * freq * 2f) * 0.15f
            val noise = (kotlin.random.Random.nextFloat() - 0.5f) * 0.06f
            // 模拟脉冲峰形：正弦正半段更高
            val pulse = if (primary > 0) primary * 0.6f else primary * 0.25f
            pulse + dicrotic + noise
        }
    }

    private fun createPulseFeaturesFromHR(hr: Int): PulseFeatures {
        val rateCategory = when {
            hr < 60  -> "slow"
            hr <= 90 -> "normal"
            else     -> "fast"
        }
        return PulseFeatures(
            position = PositionFeatures(
                floating = java.math.BigDecimal("0.25"),
                normal   = java.math.BigDecimal("0.65"),
                deep     = java.math.BigDecimal("0.10")
            ),
            rate = RateFeatures(
                rateValue         = hr,
                category          = rateCategory,
                rhythmRegularity  = java.math.BigDecimal("0.95"),
                intermittent      = false
            ),
            force = ForceFeatures(
                forceScore        = java.math.BigDecimal("0.55"),
                systolicAmplitude = java.math.BigDecimal("1.25"),
                diastolicAmplitude = java.math.BigDecimal("0.35")
            ),
            shape = ShapeFeatures(
                widthScore  = java.math.BigDecimal("0.45"),
                lengthScore = java.math.BigDecimal("0.60"),
                smoothness  = java.math.BigDecimal("0.70"),
                tautness    = java.math.BigDecimal("0.75"),
                fullness    = java.math.BigDecimal("0.50"),
                hollowness  = java.math.BigDecimal("0.15")
            ),
            momentum = MomentumFeatures(
                risingSlope       = java.math.BigDecimal("0.65"),
                fallingSlope      = java.math.BigDecimal("0.45"),
                dicroticNotchDepth = java.math.BigDecimal("0.30"),
                waveArea          = java.math.BigDecimal("125.5")
            )
        )
    }
}
