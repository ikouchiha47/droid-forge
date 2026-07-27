package com.forge.skeleton.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BLEHelper(context: Context) {

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = manager.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private val appContext = context.applicationContext

    private val cccDescriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun startScan(serviceUuid: UUID, onFound: (BluetoothDevice) -> Unit) {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onFound(result.device)
            }
        }
        scanCallback = callback
        scanner?.startScan(listOf(filter), settings, callback)
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
    }

    fun connect(device: BluetoothDevice, scope: CoroutineScope): Flow<ByteArray> = callbackFlow {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    close()
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                g.services.forEach { service ->
                    service.characteristics.forEach { ch ->
                        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            g.setCharacteristicNotification(ch, true)
                            ch.getDescriptor(cccDescriptor)?.let { d ->
                                d.value = BluetoothGattCharacteristic.ENABLE_NOTIFICATION_VALUE
                                g.writeDescriptor(d)
                            }
                        }
                    }
                }
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
                trySend(ch.value)
            }
        }
        gatt = device.connectGatt(appContext, false, callback)
        awaitClose {
            gatt?.close()
            gatt = null
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
