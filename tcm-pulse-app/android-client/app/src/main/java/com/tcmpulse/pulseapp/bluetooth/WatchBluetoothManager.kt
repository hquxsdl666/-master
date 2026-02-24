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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
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
    object Measuring : WatchConnectionState()
    object Disconnected : WatchConnectionState()
    data class Error(val message: String) : WatchConnectionState()
}

/**
 * 华为手表蓝牙管理器
 *
 * 与 Web Bluetooth (xinxiu) 相同的连接流程，移植到 Android 原生 API：
 *   requestDevice({services:['heart_rate']})  →  枚举已配对设备 + 无过滤扫描
 *   device.gatt.connect()                     →  connectGatt()
 *   getPrimaryService(0x180D)                 →  getService(HR_SERVICE_UUID)
 *   getCharacteristic(0x2A37)                 →  getCharacteristic(HR_MEASUREMENT_UUID)
 *   char.startNotifications()                 →  setCharacteristicNotification(true) + writeDescriptor
 *   characteristicvaluechanged                →  onCharacteristicChanged
 *
 * 设备发现策略（解决扫描不到问题）：
 *   1. 立即枚举系统已配对（Bonded）设备 —— 华为手表通过健康 App 配对后在此直接出现
 *   2. 并行启动无过滤器 BLE 扫描补充未配对设备
 *   3. 连接时优先用 btAdapter.getRemoteDevice(address) —— 对已配对设备无需扫描即可连接
 */
@Singleton
class WatchBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HR_SERVICE_UUID: UUID     = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    // ---- 对外 StateFlow ----

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Idle)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDeviceInfo>> = _scannedDevices.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    // ---- 内部状态 ----

    private val foundDevices = mutableMapOf<String, Pair<ScannedDeviceInfo, BluetoothDevice>>()
    private var gatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

    // ---- 设备发现 ----

    /**
     * 启动设备发现：
     * - 立即加载系统已配对设备（isBonded=true），无需等待扫描
     * - 同时启动无过滤 BLE 扫描，补充附近未配对设备
     *
     * 已配对的华为手表会在调用后立即出现在 [scannedDevices] 列表顶部。
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        foundDevices.clear()
        _scannedDevices.value = emptyList()
        _currentHeartRate.value = 0
        _connectionState.value = WatchConnectionState.Scanning

        // ① 枚举已配对设备（相当于 Web Bluetooth 选择器里的已知设备）
        btAdapter?.bondedDevices
            ?.filter { it.name?.isNotBlank() == true }
            ?.forEach { device ->
                val info = ScannedDeviceInfo(
                    address  = device.address,
                    name     = device.name ?: return@forEach,
                    rssi     = 0,          // 已配对设备无实时 RSSI
                    isBonded = true
                )
                foundDevices[device.address] = Pair(info, device)
            }

        publishDeviceList()

        // ② 无过滤 BLE 扫描（不设 ScanFilter，确保能看到所有附近设备）
        val scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            if (foundDevices.isEmpty()) {
                _connectionState.value = WatchConnectionState.Error("蓝牙未开启，请先开启手机蓝牙")
            }
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        activeScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device  = result.device
                val name    = device.name?.takeIf { it.isNotBlank() } ?: return
                val address = device.address

                // 已配对设备用扫描结果更新 RSSI，未配对设备直接新增
                val existing = foundDevices[address]
                if (existing == null || !existing.first.isBonded) {
                    val info = ScannedDeviceInfo(
                        address  = address,
                        name     = name,
                        rssi     = result.rssi,
                        isBonded = existing?.first?.isBonded ?: false
                    )
                    foundDevices[address] = Pair(info, device)
                } else {
                    // 更新已配对设备的 RSSI
                    val updated = existing.first.copy(rssi = result.rssi)
                    foundDevices[address] = Pair(updated, device)
                }
                publishDeviceList()
            }

            override fun onScanFailed(errorCode: Int) {
                val msg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED               -> "扫描已在进行中，请稍候"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描注册失败，请重启蓝牙"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED           -> "本设备不支持 BLE 扫描"
                    else -> "扫描失败 (errorCode=$errorCode)"
                }
                // 已有已配对设备时不覆盖为 Error
                if (foundDevices.isEmpty()) {
                    _connectionState.value = WatchConnectionState.Error(msg)
                }
            }
        }

        // 无过滤器 startScan —— 和 Web Bluetooth requestDevice 一样，能看到所有设备
        scanner.startScan(null, settings, activeScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        activeScanCallback?.let { btAdapter?.bluetoothLeScanner?.stopScan(it) }
        activeScanCallback = null
    }

    // ---- GATT 连接（与 xinxiu device.gatt.connect() 等价）----

    /**
     * 连接指定地址的设备。
     * 对于已配对设备可直接通过 [btAdapter.getRemoteDevice] 获取，无需依赖扫描结果。
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        stopScan()

        // 优先用 foundDevices，没有则从系统蓝牙适配器直接获取（适用于已配对设备）
        val bluetoothDevice: BluetoothDevice =
            foundDevices[address]?.second
                ?: runCatching { btAdapter?.getRemoteDevice(address) }.getOrNull()
                ?: run {
                    _connectionState.value = WatchConnectionState.Error("无法获取设备，请重新扫描")
                    return
                }

        _connectionState.value = WatchConnectionState.Connecting
        gatt = bluetoothDevice.connectGatt(
            context,
            false,           // autoConnect=false，手动触发更可靠
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
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

    // ---- GATT 回调（与 xinxiu characteristicvaluechanged 等价）----

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> gatt.discoverServices()
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
                _connectionState.value = WatchConnectionState.Error("服务发现失败 (status=$status)，请重试")
                return
            }

            // 与 xinxiu getPrimaryService('heart_rate') 等价
            val hrChar = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)

            if (hrChar == null) {
                _connectionState.value = WatchConnectionState.Error(
                    "未找到心率服务 (0x180D)\n请确认手表已开启持续心率监测"
                )
                return
            }

            // 与 xinxiu char.startNotifications() 等价
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

        // API 33+（与 xinxiu characteristicvaluechanged 等价）
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HR_MEASUREMENT_UUID
            ) {
                parseHeartRate(characteristic.value)
            }
        }
    }

    // ---- 心率解析（与 xinxiu handleHeartRate 完全一致的字节格式）----
    //
    // Byte 0: flags
    //   bit 0: 0=uint8 格式, 1=uint16 格式
    // Byte 1 (uint8) 或 Byte 1-2 little-endian (uint16): BPM 值

    private fun parseHeartRate(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt() and 0xFF
        val hr = if (flags and 0x01 != 0) {
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            if (value.size < 2) return
            value[1].toInt() and 0xFF
        }
        if (hr in 30..250) _currentHeartRate.value = hr
    }

    // ---- 内部工具 ----

    private fun publishDeviceList() {
        _scannedDevices.value = foundDevices.values
            .map { it.first }
            // 已配对设备优先显示，其次按信号强度排序
            .sortedWith(compareByDescending<ScannedDeviceInfo> { it.isBonded }
                .thenByDescending { it.rssi })
    }
}
