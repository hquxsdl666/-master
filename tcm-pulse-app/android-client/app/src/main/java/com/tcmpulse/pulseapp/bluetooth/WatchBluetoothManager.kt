package com.tcmpulse.pulseapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 华为手表心率广播接收器
 *
 * ## 原理
 * 华为 Watch GT4 第三方 App 无法通过 GATT 直接连接（需 Huawei Link Protocol v2 认证）。
 * 正确方式是启用手表「心率广播」功能，手表以 BLE Advertisement 持续广播心率，
 * App 只需扫描广播包即可，**无需建立任何连接**。
 *
 * ## 手表开启步骤
 * 方式一（手表本体）：设置 → 健康监测 → 心率广播 → 开启
 * 方式二（华为运动健康 App）：设备 → 健康管理 → 心率 → 心率广播 → 开启
 *
 * ## 广播数据解析（两种格式均支持）
 *
 * ### 格式 A：标准 ServiceData (AD Type 0x16)
 * UUID：0x180D (Heart Rate Service)
 * 数据格式与 GATT 特征值 0x2A37 完全相同：
 *   Byte 0: Flags (bit0=0 UINT8 / bit0=1 UINT16)
 *   Byte 1: Heart Rate Value (UINT8)  或
 *   Byte 1-2: Heart Rate Value (UINT16 little-endian)
 *
 * ### 格式 B：华为私有 ManufacturerSpecificData (AD Type 0xFF)
 * Company ID：0x027D (Huawei Technologies Co., Ltd., Bluetooth SIG 注册)
 * 数据布局（基于社区逆向分析，byte[0]之后为 payload）：
 *   Byte 0:   子类型标识
 *   Byte 1:   心率值（UINT8, 范围 30-220 bpm）
 *   Byte 2+:  其他生命体征或填充数据
 */
@Singleton
class WatchBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** 标准 BLE 心率服务 UUID */
        val HR_SERVICE_PARCEL_UUID: ParcelUuid =
            ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"))

        /** 华为 BLE 厂商 ID（Bluetooth SIG 注册：Huawei Technologies Co., Ltd.）*/
        const val HUAWEI_COMPANY_ID = 0x027D
    }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    // ---- 对外 StateFlow ----

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** 当前心率 BPM（0 = 无数据） */
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    /** 正在广播的设备名称（用于 UI 显示） */
    private val _sourceDevice = MutableStateFlow("")
    val sourceDevice: StateFlow<String> = _sourceDevice.asStateFlow()

    /** 扫描错误信息 */
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private var scanCallback: ScanCallback? = null

    // ---- 扫描控制 ----

    /**
     * 开始扫描 BLE 心率广播。
     * 不设置 ScanFilter（避免过滤掉华为私有格式），扫描所有附近广播包。
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        _heartRate.value = 0
        _sourceDevice.value = ""
        _error.value = ""

        val scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _error.value = "蓝牙未开启，请先开启手机蓝牙"
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                tryParseHeartRate(result)
            }

            override fun onScanFailed(errorCode: Int) {
                _error.value = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED               -> "扫描已在运行"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "蓝牙注册失败，请重启蓝牙"
                    SCAN_FAILED_FEATURE_UNSUPPORTED           -> "设备不支持 BLE"
                    else -> "扫描失败 (code=$errorCode)"
                }
                _isScanning.value = false
            }
        }

        // 无过滤器扫描，确保收到所有广播包（包括华为私有格式）
        scanner.startScan(null, settings, scanCallback)
        _isScanning.value = true
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { btAdapter?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        _isScanning.value = false
    }

    // ---- 广播包解析 ----

    private fun tryParseHeartRate(result: ScanResult) {
        val record = result.scanRecord ?: return

        // 格式 A：标准 ServiceData (0x180D) — 与 GATT 0x2A37 相同格式
        val serviceData = record.getServiceData(HR_SERVICE_PARCEL_UUID)
        if (serviceData != null && serviceData.isNotEmpty()) {
            val hr = parseStandardHRMeasurement(serviceData)
            if (hr > 0) {
                publishHR(hr, result.device.name ?: result.device.address)
                return
            }
        }

        // 格式 B：华为私有 ManufacturerSpecificData (Company ID = 0x027D)
        val huaweiData = record.getManufacturerSpecificData(HUAWEI_COMPANY_ID)
        if (huaweiData != null && huaweiData.isNotEmpty()) {
            val hr = parseHuaweiManufacturerData(huaweiData)
            if (hr > 0) {
                publishHR(hr, result.device.name ?: result.device.address)
                return
            }
        }

        // 格式 C：兜底 — 遍历所有 ManufacturerSpecificData，
        // 针对名称含 "HUAWEI"/"GT4"/"GT 4" 的设备尝试启发式解析
        val devName = result.device.name?.uppercase() ?: return
        if (devName.contains("HUAWEI") || devName.contains("GT4") ||
            devName.contains("GT 4") || devName.contains("WATCH")
        ) {
            val allMfr = record.manufacturerSpecificData
            for (i in 0 until allMfr.size()) {
                val data = allMfr.valueAt(i) ?: continue
                val hr = heuristicHRSearch(data)
                if (hr > 0) {
                    publishHR(hr, result.device.name ?: result.device.address)
                    return
                }
            }
        }
    }

    /**
     * 标准 BLE Heart Rate Measurement 格式（Bluetooth SIG 0x2A37）
     *
     * Byte 0: Flags
     *   bit0 = 0 → HR 为 UINT8（Byte 1）
     *   bit0 = 1 → HR 为 UINT16 little-endian（Byte 1-2）
     */
    private fun parseStandardHRMeasurement(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        val flags = data[0].toInt() and 0xFF
        val hr = if (flags and 0x01 != 0) {
            // UINT16
            if (data.size < 3) return 0
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            // UINT8
            if (data.size < 2) return 0
            data[1].toInt() and 0xFF
        }
        return if (hr in 30..220) hr else 0
    }

    /**
     * 华为私有格式（Company ID 0x027D）
     * 基于社区逆向分析：Byte 1 通常为心率值
     * 若 Byte 1 不在合理范围，继续尝试 Byte 0、Byte 2
     */
    private fun parseHuaweiManufacturerData(data: ByteArray): Int {
        if (data.isEmpty()) return 0
        // 按优先级尝试常见偏移
        for (offset in listOf(1, 0, 2, 3)) {
            if (offset < data.size) {
                val candidate = data[offset].toInt() and 0xFF
                if (candidate in 30..220) return candidate
            }
        }
        return 0
    }

    /**
     * 启发式搜索：对华为设备的任意 ManufacturerData，
     * 找到第一个在生理心率范围内的字节
     */
    private fun heuristicHRSearch(data: ByteArray): Int {
        for (b in data) {
            val v = b.toInt() and 0xFF
            if (v in 40..200) return v
        }
        return 0
    }

    private fun publishHR(hr: Int, deviceName: String) {
        _heartRate.value = hr
        if (_sourceDevice.value.isEmpty()) {
            _sourceDevice.value = deviceName
        }
    }
}
