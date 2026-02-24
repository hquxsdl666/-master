package com.tcmpulse.pulseapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.tcmpulse.pulseapp.data.model.ScannedDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** BLE 连接状态 */
sealed class WatchConnectionState {
    object Idle : WatchConnectionState()
    object Scanning : WatchConnectionState()
    object Connecting : WatchConnectionState()
    /** 已获得心率数据（广播模式或 GATT 通知均触发此状态） */
    object Measuring : WatchConnectionState()
    object Disconnected : WatchConnectionState()
    data class Error(val message: String) : WatchConnectionState()
}

/**
 * 华为手表蓝牙管理器
 *
 * ## 两种数据获取方式
 *
 * ### 方式一（推荐）：心率广播模式（无需配对/连接）
 * 1. 在华为手表上开启：设置 → 健康监测 → 心率广播
 *    或通过华为运动健康 App：设备 → 健康管理 → 心率 → 心率广播
 * 2. 调用 [startScan]，自动过滤带有心率服务(0x180D)的广播包
 * 3. 收到广播后直接解析心率值，[connectionState] 变为 [WatchConnectionState.Measuring]
 *
 * ### 方式二（备用）：标准 BLE GATT 连接
 * 1. 调用 [startScan] 找到设备列表
 * 2. 调用 [connectToDevice] 建立 GATT 连接
 * 3. 订阅心率特征值通知获取数据
 */
@Singleton
class WatchBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(HR_SERVICE_UUID)
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    // ---- 对外暴露的 StateFlow ----

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Idle)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    /** GATT 备用路径中扫描到的设备列表（广播模式下为空） */
    private val _scannedDevices = MutableStateFlow<List<ScannedDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDeviceInfo>> = _scannedDevices.asStateFlow()

    /** 当前心率 BPM，0 表示无数据 */
    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    // ---- 内部状态 ----

    private val foundDevices = mutableMapOf<String, Pair<ScannedDeviceInfo, BluetoothDevice>>()
    private var gatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

    // ---- 扫描（广播优先，GATT 备用） ----

    /**
     * 启动 BLE 扫描。
     *
     * - 过滤条件：仅搜索广播了心率服务(0x180D)的设备
     * - 如果广播包内含心率数据（心率广播模式）→ 直接解析，无需连接
     * - 如果仅广播服务 UUID 但无数据（GATT 模式）→ 加入设备列表供用户选择
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        foundDevices.clear()
        _scannedDevices.value = emptyList()
        _currentHeartRate.value = 0
        _connectionState.value = WatchConnectionState.Scanning

        val scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = WatchConnectionState.Error("蓝牙未开启，请先开启手机蓝牙")
            return
        }

        // 只过滤含心率服务 UUID 的广播，大幅减少无关设备干扰
        val filter = ScanFilter.Builder()
            .setServiceUuid(HR_SERVICE_PARCEL_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        activeScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // ① 尝试从广播包的 ServiceData 字段读取心率（心率广播模式）
                val serviceData = result.scanRecord?.getServiceData(HR_SERVICE_PARCEL_UUID)
                if (serviceData != null && serviceData.isNotEmpty()) {
                    parseHeartRate(serviceData)
                    // 广播数据到手，直接进入 Measuring 状态
                    if (_connectionState.value != WatchConnectionState.Measuring) {
                        _connectionState.value = WatchConnectionState.Measuring
                    }
                    return
                }

                // ② 无广播心率数据 → 加入 GATT 设备列表
                val device = result.device
                val name = device.name?.takeIf { it.isNotBlank() } ?: return
                val address = device.address
                if (!foundDevices.containsKey(address)) {
                    val info = ScannedDeviceInfo(address, name, result.rssi)
                    foundDevices[address] = Pair(info, device)
                    _scannedDevices.value = foundDevices.values
                        .map { it.first }
                        .sortedByDescending { it.rssi }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val hint = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描注册失败，请重启蓝牙"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "设备不支持 BLE 扫描"
                    else -> "扫描失败 (错误码: $errorCode)"
                }
                _connectionState.value = WatchConnectionState.Error(hint)
            }
        }

        scanner.startScan(listOf(filter), settings, activeScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        activeScanCallback?.let {
            btAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        activeScanCallback = null
    }

    // ---- GATT 备用连接 ----

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        stopScan()
        val entry = foundDevices[address]
        if (entry == null) {
            _connectionState.value = WatchConnectionState.Error("设备未找到，请重新扫描")
            return
        }
        _connectionState.value = WatchConnectionState.Connecting
        gatt = entry.second.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _currentHeartRate.value = 0
        _connectionState.value = WatchConnectionState.Idle
    }

    // ---- GATT 回调 ----

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    this@WatchBluetoothManager.gatt = null
                    _currentHeartRate.value = 0
                    _connectionState.value = WatchConnectionState.Disconnected
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = WatchConnectionState.Error("GATT 服务发现失败，请重试")
                return
            }
            val hrChar = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (hrChar == null) {
                _connectionState.value = WatchConnectionState.Error(
                    "未找到心率服务 (0x180D)\n" +
                    "建议：在手表上开启「心率广播」功能后重新扫描"
                )
                return
            }
            gatt.setCharacteristicNotification(hrChar, true)
            val descriptor = hrChar.getDescriptor(CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
            _connectionState.value = WatchConnectionState.Measuring
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) parseHeartRate(value)
        }

        // API < 33
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == HR_MEASUREMENT_UUID) parseHeartRate(characteristic.value)
            }
        }
    }

    // ---- 心率解析（BLE 0x2A37 规范）----

    private fun parseHeartRate(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt() and 0xFF
        val hr = if (flags and 0x01 != 0) {
            // UINT16 格式
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            // UINT8 格式
            if (value.size < 2) return
            value[1].toInt() and 0xFF
        }
        if (hr in 30..250) _currentHeartRate.value = hr
    }
}
