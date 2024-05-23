package com.example.starling.connection.server

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import com.example.starling.Log
import com.example.starling.Logger
import com.example.starling.connection.DeviceConnection
import com.example.starling.connection.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.ktx.suspend

class ServerConnection(
    private val serverManager: ServerManager,
    logger: Logger,
    scope: CoroutineScope,
    private val bluetoothDevice: BluetoothDevice,
) : DeviceConnection(serverManager.context), CoroutineScope, DataReceivedCallback {
  companion object {
    private const val TAG = "ServerConnection"
  }

  private val log = Log(logger, TAG)

  override val coroutineContext = Job(scope.coroutineContext.job)

  var dataCharacteristic: BluetoothGattCharacteristic? = null

  init {
    log.d("initializing server connection to ${bluetoothDevice.address}")
    useServer(serverManager)
    launch {
      try {
        connect(bluetoothDevice)
          .retry(3)
          //.timeout(4000)
          //.useAutoConnect(true)
          .suspend()
        log.d("Connected to device: ${bluetoothDevice.address}")
      } catch (e: Exception) {
        log.catch(e, "Failed to connect: ${bluetoothDevice.address}")
      }
    }
  }

  override fun getBluetoothDevice() = bluetoothDevice

  override fun onServerReady(server: BluetoothGattServer) {
    log.d("onServerReady")
    server.getService(serverManager.serverData.serviceUUID)?.let {
      dataCharacteristic = it.getCharacteristic(serverManager.serverData.characteristicUUID)
    }
  }

  override fun initialize() {
    log.d("initialize")

    setWriteCallback(dataCharacteristic!!)
      .with(this@ServerConnection)
      .then { log.d("Characteristic write callback closed") }

    launch {
      try {
        val mtu = requestMtu(517).suspend()
        log.d("Got an MTU value of $mtu")

        waitUntilNotificationsEnabled(dataCharacteristic).suspend()
        log.d("Notifications enabled: ${bluetoothDevice.address}")
        serverManager.onDeviceFullyConnected(this@ServerConnection);
      } catch (e: Exception) {
        log.catch(e, "Failed to enable notifications: ${bluetoothDevice.address}")
        disconnect()
      }
    }
  }

  override suspend fun sendPacket(value: ByteArray): Unit = withContext(this.coroutineContext) {
    try {
      log.d("Sending packet to server: ${bluetoothDevice.address}")
      sendNotification(dataCharacteristic, value).suspend()
      log.d("Sent ${value.size} bytes to device ${bluetoothDevice.address}")
    } catch (e: Exception) {
      log.catch(e,"Failed to send data to connected device: ${bluetoothDevice.address}")
    }
  }



  override fun onDataReceived(device: BluetoothDevice, data: Data) {
    log.d("Read ${data.size()} bytes")
    serverManager.packetReceived(this, data)
  }

  // There are no services that we need from the connecting device, but
  // if there were, we could specify them here.
  override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
    log.d("isRequiredServiceSupported")
    return true
  }

  override fun onServicesInvalidated() {
    // This is the place to nullify characteristics obtained above.
    log.d("onServicesInvalidated")
  }

  override fun log(priority: Int, message: String) {
    log.log(priority, message)
  }
}

interface ServerConnectionCallback {
  fun onDeviceFullyConnected(connection: ServerConnection)
}
