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
import android.os.ParcelUuid
import com.tcmpulse.pulseapp.data.model.ScannedDeviceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class WatchConnectionState {
    object Idle : WatchConnectionState()
    object Scanning : WatchConnectionState()
    object Connecting : WatchConnectionState()
    object Measuring : WatchConnectionState()
    object Disconnected : WatchConnectionState()
    data class Error(val message: String) : WatchConnectionState()
}

@Singleton
class WatchBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val HR_SERVICE_UUID: UUID     = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(HR_SERVICE_UUID)
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val HUAWEI_COMPANY_ID   = 0x027D
    }

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btAdapter get() = btManager.adapter

    private val _connectionState = MutableStateFlow<WatchConnectionState>(WatchConnectionState.Idle)
    val connectionState: StateFlow<WatchConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDeviceInfo>> = _scannedDevices.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate: StateFlow<Int> = _currentHeartRate.asStateFlow()

    private val foundDevices = mutableMapOf<String, Pair<ScannedDeviceInfo, BluetoothDevice>>()
    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null

    // ---- 扫描 ----

    @SuppressLint("MissingPermission")
    fun startScan() {
        foundDevices.clear()
        _scannedDevices.value = emptyList()
        _currentHeartRate.value = 0
        _connectionState.value = WatchConnectionState.Scanning

        // 立即加入已配对设备（华为手表通过健康App配对后直接出现）
        btAdapter?.bondedDevices
            ?.filter { it.name?.isNotBlank() == true }
            ?.forEach { device ->
                val info = ScannedDeviceInfo(device.address, device.name!!, 0, isBonded = true)
                foundDevices[device.address] = Pair(info, device)
            }
        publishList()

        // 无过滤器 BLE 扫描，补充未配对设备
        val scanner = btAdapter?.bluetoothLeScanner
        if (scanner == null) {
            if (foundDevices.isEmpty())
                _connectionState.value = WatchConnectionState.Error("蓝牙未开启")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name?.takeIf { it.isNotBlank() } ?: return
                val addr = device.address

                // 尝试从广播包直接解析心率（如果手表在广播心率数据）
                parseBroadcastHR(result)

                val existing = foundDevices[addr]
                val info = ScannedDeviceInfo(addr, name, result.rssi, existing?.first?.isBonded ?: false)
                foundDevices[addr] = Pair(info, device)
                publishList()
            }

            override fun onScanFailed(errorCode: Int) {
                if (foundDevices.isEmpty())
                    _connectionState.value = WatchConnectionState.Error("扫描失败 (code=$errorCode)")
            }
        }
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let { btAdapter?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
    }

    // ---- GATT 连接 ----

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        stopScan()
        val device: BluetoothDevice =
            foundDevices[address]?.second
                ?: runCatching { btAdapter?.getRemoteDevice(address) }.getOrNull()
                ?: run {
                    _connectionState.value = WatchConnectionState.Error("无法获取设备")
                    return
                }
        _connectionState.value = WatchConnectionState.Connecting
        // autoConnect=false → 快速连接（适用于已配对设备）
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
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
            // ★ 关键修复：先检查 status，status≠0 表示连接失败（如 status=133 设备忙）
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.close()
                this@WatchBluetoothManager.gatt = null
                val hint = when (status) {
                    133 -> "连接失败(133)：手表当前被其他应用占用\n" +
                            "请退出华为运动健康App后重试，或在手表上开启心率广播"
                    8   -> "连接失败(8)：连接被设备拒绝，请确认手表蓝牙已开启"
                    22  -> "连接失败(22)：认证失败，请在手表上确认配对"
                    else -> "连接失败(status=$status)，请重试"
                }
                _connectionState.value = WatchConnectionState.Error(hint)
                return
            }

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
                _connectionState.value = WatchConnectionState.Error("服务发现失败(status=$status)")
                return
            }
            val hrChar = gatt.getService(HR_SERVICE_UUID)
                ?.getCharacteristic(HR_MEASUREMENT_UUID)
            if (hrChar == null) {
                _connectionState.value = WatchConnectionState.Error(
                    "未找到心率服务(0x180D)\n请确认手表已开启持续心率监测"
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
            if (characteristic.uuid == HR_MEASUREMENT_UUID) parseHRBytes(value)
        }

        // API < 33
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HR_MEASUREMENT_UUID
            ) parseHRBytes(characteristic.value)
        }
    }

    // ---- 广播心率解析（心率广播模式备用）----

    private fun parseBroadcastHR(result: ScanResult) {
        val record = result.scanRecord ?: return
        // 标准 ServiceData(0x180D)
        record.getServiceData(HR_SERVICE_PARCEL_UUID)
            ?.takeIf { it.isNotEmpty() }
            ?.let { d -> parseHRBytes(d).also { if (_currentHeartRate.value > 0) _connectionState.value = WatchConnectionState.Measuring } }
            ?: run {
                // 华为私有 ManufacturerData(0x027D)
                record.getManufacturerSpecificData(HUAWEI_COMPANY_ID)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { d ->
                        for (offset in listOf(1, 0, 2, 3)) {
                            if (offset < d.size) {
                                val v = d[offset].toInt() and 0xFF
                                if (v in 30..220) { _currentHeartRate.value = v; _connectionState.value = WatchConnectionState.Measuring; return }
                            }
                        }
                    }
            }
    }

    // ---- 心率字节解析（标准 0x2A37 格式）----

    private fun parseHRBytes(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt() and 0xFF
        val hr = if (flags and 0x01 != 0) {
            if (value.size < 3) return
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            if (value.size < 2) return
            value[1].toInt() and 0xFF
        }
        if (hr in 30..220) _currentHeartRate.value = hr
    }

    // ---- 工具 ----

    private fun publishList() {
        _scannedDevices.value = foundDevices.values
            .map { it.first }
            .sortedWith(compareByDescending<ScannedDeviceInfo> { it.isBonded }.thenByDescending { it.rssi })
    }
}
