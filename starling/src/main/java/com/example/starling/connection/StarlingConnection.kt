package com.example.starling.connection

import android.content.Context
import android.util.Log
import com.example.starling.DeviceAddress
import com.example.starling.connection.client.ClientConnection
import com.example.starling.connection.server.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend

class StarlingConnection(
  server: ServerConnection?,
  client: ClientConnection?
) {
  var server: ServerConnection? = null
    get() = field
    set(value) {
      val clientID = client?.bluetoothDevice?.address
      val serverID = value?.bluetoothDevice?.address

      if (value != null && clientID != null && clientID != serverID) {
        throw IllegalArgumentException("server must have same address as corresponding client")
      }

      field = value
    }

  var client: ClientConnection? = null
    get() = field
    set(value) {
      val serverID = server?.bluetoothDevice?.address
      val clientID = value?.bluetoothDevice?.address

      if (value != null && serverID != null && serverID != clientID) {
        throw IllegalArgumentException("client must have same address as corresponding server")
      }

      field = value
    }

  init {
    if (server == null && client == null) {
      throw IllegalArgumentException("StarlingConnection should be initialized with at least one server or client")
    }

    this.server = server
    this.client = client
  }

  val address: DeviceAddress get() {
    val addrString = server?.bluetoothDevice?.address ?: client!!.bluetoothDevice.address
    return DeviceAddress(addrString)
  }

  val maximumWriteLength: Int? get() = (server ?: client ?: run {
    Log.e(TAG, "Attempted to get maximumWriteLength of unknown connection")
    return null
  }).maximumWriteLength

  suspend fun sendPacket(packet: ByteArray) {
    val connection: DeviceConnection = server ?: client ?: run {
      Log.e(TAG, "Attempted to send a message to an unknown recipient")
      return
    }

    connection.sendPacket(packet)
  }

  suspend fun disconnect(scope: CoroutineScope) {
    val serverResult = scope.async { server?.disconnect()?.suspend() }
    val clientResult = scope.async { client?.disconnect()?.suspend() }

    serverResult.await()
    clientResult.await()
  }

  companion object {
    const val TAG = "starling-connection"
  }
}

abstract class DeviceConnection(context: Context) : BleManager(context) {
  val maximumWriteLength: Int get() = mtu - 3
  val address: DeviceAddress get() = DeviceAddress(bluetoothDevice!!.address)

  abstract suspend fun sendPacket(value: ByteArray)
}
