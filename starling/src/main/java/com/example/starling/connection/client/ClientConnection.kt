package com.example.starling.connection.client

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.example.starling.Log
import com.example.starling.Logger
import com.example.starling.connection.DeviceConnection
import com.example.starling.connection.ServerData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.exception.RequestFailedException
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver
import kotlin.coroutines.CoroutineContext

class ClientConnection(
  context: Context,
  logger: Logger,
  scope: CoroutineScope,
  private val bluetoothDevice: BluetoothDevice,
  val serverData: ServerData,
  val callback: ClientConnectionCallback
) :
  DeviceConnection(context), CoroutineScope, ConnectionObserver, DataReceivedCallback {

  companion object {
    const val TAG = "ClientConnection"
  }

  private val log = Log(logger, TAG)

  override val coroutineContext: CoroutineContext = Job(scope.coroutineContext.job)

  private var dataCharacteristic: BluetoothGattCharacteristic? = null

  init {
    launch {
      try {
        connect(bluetoothDevice)
          .retry(3)
          .useAutoConnect(true)
          .suspend()
      } catch (e: RequestFailedException) {
        log.catch(e, "Request failed with status ${e.status}")
      }
    }

    connectionObserver = this
  }

  override fun getBluetoothDevice() = bluetoothDevice

  override fun onDeviceReady() {
    log.d("Device ready: ${bluetoothDevice.address}")
  }

  override fun initialize() {
    log.d("initialize")

    launch {
      try {
        val mtu = requestMtu(517).suspend()
        log.d("Got an MTU value of $mtu")

        setNotificationCallback(dataCharacteristic)
          .with(this@ClientConnection)
          .then { log.d("Notification callback ended") }

        enableNotifications(dataCharacteristic).suspend()
        log.d("Successfully enabled notifications")
        callback.onDeviceFullyConnected(this@ClientConnection)
      } catch (e: Exception) {
        log.catch(e, "Failed to enable notifications for client connection")
        disconnect()
      }
    }
  }

  override suspend fun sendPacket(value: ByteArray): Unit = withContext(this.coroutineContext) {
    try {
      log.d("Sending packet to client: ${bluetoothDevice.address}")
      writeCharacteristic(
        dataCharacteristic,
        value,
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
      ).suspend()
      log.d("Sent ${value.size} bytes to device ${bluetoothDevice.address}")
    } catch (e: Exception) {
      log.catch(e, "Failed to send data to connected device: ${bluetoothDevice.address}")
    }
  }

  override fun onDataReceived(device: BluetoothDevice, data: Data) {
    log.d("Read ${data.size()} bytes")
    callback.messageReceived(this, data)
  }

  override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    log.d("isRequiredServiceSupported")

    dataCharacteristic = getCharacteristic(gatt)
    return if (dataCharacteristic != null) {
      log.d("Found data characteristic")
      true
    } else {
      log.d("data characteristic not found!")
      false
    }
  }

  override fun log(priority: Int, message: String) {
    log.log(priority, message)
  }

  override fun onDeviceConnecting(device: BluetoothDevice) {}

  override fun onDeviceConnected(device: BluetoothDevice) {
    log.d("Client device connected: ${device.address}")
  }

  override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
    callback.onDeviceDisconnected(this)
  }

  override fun onDeviceReady(device: BluetoothDevice) {}
  override fun onDeviceDisconnecting(device: BluetoothDevice) {}
  override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
    callback.onDeviceDisconnected(this)
  }

  fun getCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? {
    val service = gatt.getService(serverData.serviceUUID) ?: run {
      log.w("getCharacteristic: our service was not found")
      return null
    }
    return service.getCharacteristic(serverData.characteristicUUID) ?: run {
      log.w("getCharacteristic: our characteristic was not found")
      return null
    }
  }
}

interface ClientConnectionCallback {
  fun onDeviceFullyConnected(connection: ClientConnection)
  fun onDeviceDisconnected(connection: ClientConnection)
  fun messageReceived(connection: ClientConnection, data: Data)
}
