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
    object Measuring : WatchConnectionState()          // 已连接，正在接收心率
    object Disconnected : WatchConnectionState()
    data class Error(val message: String) : WatchConnectionState()
}

/**
 * 华为手表蓝牙管理器
 *
 * 使用标准 BLE GATT 心率服务 (0x180D) 读取心率数据。
 * 华为 WATCH GT4 支持标准心率 GATT 服务，无需 HMS SDK。
 *
 * 注意：原始 PPG 信号需要华为 Health Kit SDK；
 * 此处读取的是已处理的心率值（BPM），波形由客户端合成。
 */
@Singleton
class WatchBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // 标准 BLE 心率服务 UUID (Bluetooth SIG 规范)
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    // ---- 对外暴露的 StateFlow ----

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Idle)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDeviceInfo>> = _scannedDevices.asStateFlow()

    /** 当前心率（BPM），0 表示无数据 */
    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    // ---- 内部状态 ----

    private val foundDevices = mutableMapOf<String, Pair<ScannedDeviceInfo, BluetoothDevice>>()
    private var gatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

    // ---- 扫描 ----

    @SuppressLint("MissingPermission")
    fun startScan() {
        foundDevices.clear()
        _scannedDevices.value = emptyList()
        _connectionState.value = WatchConnectionState.Scanning

        val scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = WatchConnectionState.Error("蓝牙未开启，请先开启蓝牙")
            return
        }

        activeScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name?.takeIf { it.isNotBlank() } ?: return
                val address = device.address
                if (!foundDevices.containsKey(address)) {
                    val info = ScannedDeviceInfo(address, name, result.rssi)
                    foundDevices[address] = Pair(info, device)
                    _scannedDevices.value = foundDevices.values
                        .map { it.first }
                        .sortedByDescending { it.rssi }  // 信号强的排前面
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _connectionState.value = WatchConnectionState.Error("蓝牙扫描失败 (错误码: $errorCode)")
            }
        }
        scanner.startScan(activeScanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        activeScanCallback?.let {
            btAdapter?.bluetoothLeScanner?.stopScan(it)
        }
        activeScanCallback = null
    }

    // ---- 连接 ----

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
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt.discoverServices()
                }
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
                _connectionState.value = WatchConnectionState.Error("GATT 服务发现失败")
                return
            }

            val hrChar = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)

            if (hrChar == null) {
                _connectionState.value = WatchConnectionState.Error(
                    "未发现标准心率服务（0x180D）\n" +
                    "请确认手表已开启心率监测，或该设备支持标准 BLE 心率协议"
                )
                return
            }

            // 订阅心率通知
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

        // API 33+ 新回调
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) parseHeartRate(value)
        }

        // API < 33 旧回调
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

    // ---- 心率解析（BLE 心率测量特征值格式：Bluetooth SIG 规范 0x2A37）----
    private fun parseHeartRate(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt() and 0xFF
        // Bit 0: 0=UINT8 格式, 1=UINT16 格式
        val hr = if (flags and 0x01 != 0) {
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            if (value.size < 2) return
            value[1].toInt() and 0xFF
        }
        // 合理性校验
        if (hr in 30..250) {
            _currentHeartRate.value = hr
        }
    }
}
