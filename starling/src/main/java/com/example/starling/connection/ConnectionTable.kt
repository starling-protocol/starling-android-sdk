package com.example.starling.connection

import com.example.starling.DeviceAddress
import com.example.starling.Log
import com.example.starling.Logger
import com.example.starling.connection.client.ClientConnection
import com.example.starling.connection.server.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class ConnectionTable(scope: CoroutineScope, logger: Logger) : CoroutineScope, Iterable<StarlingConnection> {
  override val coroutineContext = Job(scope.coroutineContext.job)

  private val log = Log(logger, TAG)

  private val connections = HashMap<DeviceAddress, StarlingConnection>()

  fun get(address: DeviceAddress?): StarlingConnection? {
    return connections[address]
  }

  fun connectServer(server: ServerConnection): Boolean {
    log.d("connect server: ${server.address}")
    val address = server.address
    val newConnection = !connections.containsKey(address)

    connections[address]?.let { it.server = server } ?: run {
      connections[address] = StarlingConnection(server, null)
    }

    return newConnection
  }

  fun disconnectServer(server: ServerConnection): Boolean {
    log.d("disconnect server: ${server.address}")
    val address = server.address
    val connection = connections[address] ?: return true

    if (connection.client == null) {
      connections.remove(address)
      return true
    } else {
      connection.server = null
      return false
    }
  }

  fun connectClient(client: ClientConnection): Boolean {
    log.d("connect client: ${client.address}")
    val address = client.address
    val newConnection = !connections.containsKey(address)

    connections[address]?.let { it.client = client } ?: run {
      connections[address] = StarlingConnection(null, client)
    }

    return newConnection
  }

  fun disconnectClient(client: ClientConnection): Boolean {
    log.d("disconnect client: ${client.address}")
    val address = client.address
    val connection = connections[address] ?: return true

    if (connection.server == null) {
      connections.remove(address)
      return true
    } else {
      connection.client = null
      return false
    }
  }

  fun sendPacket(deviceAddress: DeviceAddress, packet: ByteArray) {
    log.d("sendPacket ${connections[deviceAddress]?.address} - ${packet.size} bytes")
    val connection = connections[deviceAddress] ?: run {
      log.e("Could not find device with address $deviceAddress, when sending packet")
      return
    }

    launch { connection.sendPacket(packet) }
  }

  companion object {
    const val TAG = "ConnectionTable"
  }

  override fun iterator(): Iterator<StarlingConnection> {
    return connections.values.iterator()
  }
}
