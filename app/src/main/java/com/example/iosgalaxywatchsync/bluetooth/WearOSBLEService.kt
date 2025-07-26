package com.example.iosgalaxywatchsync.bluetooth

import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.iosgalaxywatchsync.models.BLEDataPacket
import com.example.iosgalaxywatchsync.models.HealthDataEntry
import com.example.iosgalaxywatchsync.models.HealthDataType
import com.google.gson.Gson
import java.security.MessageDigest
import java.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** BLE GATT Server for Wear OS to act as peripheral */
class WearOSBLEService(private val context: Context) {

    companion object {
        private const val TAG = "WearOSBLEService"

        // Custom service UUID for health data sync
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

        // Characteristic UUIDs
        val DATA_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CONTROL_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E")

        // Data packet size limit for BLE
        private const val MAX_PACKET_SIZE = 512
    }

    private val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    private val gson = Gson()
    private val pendingData = mutableListOf<HealthDataEntry>()
    private var currentTransfer: DataTransfer? = null

    private val _connectionState = MutableStateFlow(BLEConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BLEConnectionState> = _connectionState

    private val _transferStatus = MutableStateFlow(BLETransferStatus.IDLE)
    val transferStatus: StateFlow<BLETransferStatus> = _transferStatus

    // Channel for sync completion events
    val syncCompletionChannel = Channel<Boolean>(Channel.BUFFERED)

    data class DataTransfer(
            val packets: List<BLEDataPacket>,
            var currentPacketIndex: Int = 0,
            val dataType: HealthDataType
    )

    enum class BLEConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    enum class BLETransferStatus {
        IDLE,
        PREPARING,
        TRANSFERRING,
        COMPLETED,
        ERROR
    }

    private val gattServerCallback =
            object : BluetoothGattServerCallback() {
                override fun onConnectionStateChange(
                        device: BluetoothDevice?,
                        status: Int,
                        newState: Int
                ) {
                    super.onConnectionStateChange(device, status, newState)

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "Device connected: ${device?.address}")
                            _connectionState.value = BLEConnectionState.CONNECTED
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            Log.d(TAG, "Device disconnected: ${device?.address}")
                            _connectionState.value = BLEConnectionState.DISCONNECTED
                            currentTransfer = null
                        }
                    }
                }

                override fun onCharacteristicReadRequest(
                        device: BluetoothDevice?,
                        requestId: Int,
                        offset: Int,
                        characteristic: BluetoothGattCharacteristic?
                ) {
                    super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

                    when (characteristic?.uuid) {
                        DATA_CHAR_UUID -> {
                            handleDataRead(device, requestId, offset)
                        }
                        STATUS_CHAR_UUID -> {
                            handleStatusRead(device, requestId, offset)
                        }
                        else -> {
                            gattServer?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_FAILURE,
                                    0,
                                    null
                            )
                        }
                    }
                }

                override fun onCharacteristicWriteRequest(
                        device: BluetoothDevice?,
                        requestId: Int,
                        characteristic: BluetoothGattCharacteristic?,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?
                ) {
                    super.onCharacteristicWriteRequest(
                            device,
                            requestId,
                            characteristic,
                            preparedWrite,
                            responseNeeded,
                            offset,
                            value
                    )

                    when (characteristic?.uuid) {
                        CONTROL_CHAR_UUID -> {
                            handleControlWrite(device, requestId, value, responseNeeded)
                        }
                        else -> {
                            if (responseNeeded) {
                                gattServer?.sendResponse(
                                        device,
                                        requestId,
                                        BluetoothGatt.GATT_FAILURE,
                                        0,
                                        null
                                )
                            }
                        }
                    }
                }
            }

    private val advertiseCallback =
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "BLE advertising started successfully")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE advertising failed with error: $errorCode")
                }
            }

    fun startAdvertising(): Boolean {
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }

        try {
            setupGattServer()
            setupAdvertising()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE service", e)
            return false
        }
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        gattServer = null
        _connectionState.value = BLEConnectionState.DISCONNECTED
    }

    fun addDataForSync(data: List<HealthDataEntry>) {
        pendingData.clear()
        pendingData.addAll(data)
        Log.d(TAG, "Added ${data.size} entries for BLE sync")
    }

    private fun setupGattServer() {
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Data characteristic (read)
        val dataCharacteristic =
                BluetoothGattCharacteristic(
                        DATA_CHAR_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                )

        // Control characteristic (write)
        val controlCharacteristic =
                BluetoothGattCharacteristic(
                        CONTROL_CHAR_UUID,
                        BluetoothGattCharacteristic.PROPERTY_WRITE,
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                )

        // Status characteristic (read)
        val statusCharacteristic =
                BluetoothGattCharacteristic(
                        STATUS_CHAR_UUID,
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ
                )

        service.addCharacteristic(dataCharacteristic)
        service.addCharacteristic(controlCharacteristic)
        service.addCharacteristic(statusCharacteristic)

        gattServer?.addService(service)
    }

    private fun setupAdvertising() {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val settings =
                AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                        .setConnectable(true)
                        .build()

        val data =
                AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .setIncludeTxPowerLevel(false)
                        .addServiceUuid(ParcelUuid(SERVICE_UUID))
                        .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun handleDataRead(device: BluetoothDevice?, requestId: Int, offset: Int) {
        val transfer = currentTransfer
        if (transfer == null) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            return
        }

        if (transfer.currentPacketIndex >= transfer.packets.size) {
            // Transfer complete
            gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    byteArrayOf()
            )
            _transferStatus.value = BLETransferStatus.COMPLETED
            currentTransfer = null
            syncCompletionChannel.trySend(true)
            return
        }

        val packet = transfer.packets[transfer.currentPacketIndex]
        val serializedPacket = gson.toJson(packet).toByteArray()

        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, serializedPacket)
        transfer.currentPacketIndex++

        Log.d(TAG, "Sent packet ${transfer.currentPacketIndex}/${transfer.packets.size}")
    }

    private fun handleStatusRead(device: BluetoothDevice?, requestId: Int, offset: Int) {
        val status =
                mapOf(
                        "pendingDataCount" to pendingData.size,
                        "transferStatus" to _transferStatus.value.name,
                        "connectionState" to _connectionState.value.name
                )

        val statusJson = gson.toJson(status).toByteArray()
        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, statusJson)
    }

    private fun handleControlWrite(
            device: BluetoothDevice?,
            requestId: Int,
            value: ByteArray?,
            responseNeeded: Boolean
    ) {
        val command = value?.let { String(it) } ?: ""

        when (command) {
            "START_SYNC" -> {
                startDataTransfer()
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
            "RESET" -> {
                currentTransfer = null
                _transferStatus.value = BLETransferStatus.IDLE
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
            else -> {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }

    private fun startDataTransfer() {
        if (pendingData.isEmpty()) {
            _transferStatus.value = BLETransferStatus.COMPLETED
            return
        }

        _transferStatus.value = BLETransferStatus.PREPARING

        // Group data by type for efficient transfer
        val groupedData = pendingData.groupBy { it.type }

        // For simplicity, transfer all data as one type
        val allDataJson = gson.toJson(pendingData)
        val packets = createDataPackets(allDataJson.toByteArray(), HealthDataType.DAILY_METRICS)

        currentTransfer = DataTransfer(packets, 0, HealthDataType.DAILY_METRICS)
        _transferStatus.value = BLETransferStatus.TRANSFERRING

        Log.d(TAG, "Started data transfer with ${packets.size} packets")
    }

    private fun createDataPackets(data: ByteArray, dataType: HealthDataType): List<BLEDataPacket> {
        val packets = mutableListOf<BLEDataPacket>()
        val totalPackets = (data.size + MAX_PACKET_SIZE - 1) / MAX_PACKET_SIZE

        for (i in 0 until totalPackets) {
            val start = i * MAX_PACKET_SIZE
            val end = minOf(start + MAX_PACKET_SIZE, data.size)
            val payload = data.sliceArray(start until end)

            val checksum = calculateChecksum(payload)

            packets.add(
                    BLEDataPacket(
                            sequenceNumber = i,
                            totalPackets = totalPackets,
                            dataType = dataType,
                            payload = payload,
                            checksum = checksum
                    )
            )
        }

        return packets
    }

    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
