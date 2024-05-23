package com.example.starling.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.example.starling.Log
import com.example.starling.Logger
import com.example.starling.connection.server.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import no.nordicsemi.android.ble.BleServerManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ServerObserver
import java.util.UUID
import kotlin.coroutines.CoroutineContext

data class ServerData(val serviceUUID: UUID, val characteristicUUID: UUID, val appleBit: Int) {}

class ServerManager(
    val context: Context,
    logger: Logger,
    scope: CoroutineScope,
    public val serverData: ServerData,
    val callback: ServerManagerCallback
) : BleServerManager(context), ServerObserver, CoroutineScope {
  companion object {
    private const val TAG = "ServerManager"
  }

  private val log = Log(logger, TAG)

  override val coroutineContext: CoroutineContext = Job(scope.coroutineContext.job)

  private var connections: MutableMap<String, ServerConnection> = mutableMapOf()

  val dataCharacteristic = characteristic(
    serverData.characteristicUUID,
    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
    BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
  )

  override fun initializeServer(): List<BluetoothGattService> {
    log.d("initializeServer")

    setServerObserver(this)

    return listOf(
      service(
        serverData.serviceUUID, dataCharacteristic
      )
    )
  }

  override fun onServerReady() {
    log.d("onServerReady")
  }

  override fun onDeviceConnectedToServer(device: BluetoothDevice) {
    log.d("onDeviceConnectedToServer: ${device.address}")

    log.d("Existing connections:")
    connections.forEach {
      log.d("- ${it.key}")
    }

    connections[device.address] = ServerConnection(this, log.logger, this, device)
  }

  override fun onDeviceDisconnectedFromServer(device: BluetoothDevice) {
    log.d("onDeviceDisconnectedFromServer: ${device.address}")

    connections[device.address]?.apply {
      callback.onDeviceDisconnectedFromServer(this)
    }
    connections.remove(device.address)
  }

  fun packetReceived(connection: ServerConnection, data: Data) {
    callback.onServerReceivedPacket(connection, data)
  }

  fun onDeviceFullyConnected(connection: ServerConnection) {
    log.d("onDeviceFullyConnected: ${connection.address}")
    callback.onDeviceConnectedToServer(connection)
  }

  override fun log(priority: Int, message: String) {
    log.log(priority, message)
  }
}

interface ServerManagerCallback {
  fun onDeviceConnectedToServer(connection: ServerConnection)
  fun onDeviceDisconnectedFromServer(connection: ServerConnection)
  fun onServerReceivedPacket(connection: ServerConnection, data: Data)
}
