package com.example.starling.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.starling.Log
import com.example.starling.Logger
import com.example.starling.connection.client.ClientConnection
import com.example.starling.connection.client.ClientConnectionCallback
import kotlinx.coroutines.CoroutineScope
import no.nordicsemi.android.ble.data.Data
import kotlin.experimental.and

@SuppressLint("MissingPermission")
class BleScanner(
    val context: Context,
    logger: Logger,
    val serverData: ServerData,
    val scope: CoroutineScope,
    val callback: BleScannerCallback
) : ScanCallback(), ClientConnectionCallback {
  val bluetoothAdapter: BluetoothAdapter by lazy {
    val bluetoothManager = context.getSystemService(
      Context.BLUETOOTH_SERVICE
    ) as BluetoothManager
    bluetoothManager.adapter
  }

  private val log = Log(logger, TAG)

  private var scanning = false
  private var connections: MutableMap<String, ClientConnection> = mutableMapOf()

  fun startScanning() {

    if (!bluetoothAdapter.isEnabled) {
      log.w("Bluetooth is currently turned off")
    }

    // Setup filter and scanner
    /*val filter = ScanFilter.Builder().setServiceUuid(
      ParcelUuid(serverData.serviceUUID)
    ).build()*/

    //val filterList : ArrayList<ScanFilter> = ArrayList()
    //filterList.add(filter)

    val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    if (bluetoothLeScanner == null) {
      log.d("Bluetooth was not enabled when initiating scanning")
    }

    // Scanning logic
    if (!scanning) {
      scanning = true
      bluetoothLeScanner.startScan(/*filterList*/ null,
        ScanSettings.Builder().build(),
        this
      )
    } else {
      stopScanning()
    }
  }

  override fun onScanResult(callbackType: Int, result: ScanResult) {
    super.onScanResult(callbackType, result)

    if (connections.contains(result.device.address)) return

    var foundValidDevice = false

    val scanRecord = result.scanRecord ?: return

    if (scanRecord.serviceUuids?.contains(ParcelUuid(serverData.serviceUUID)) == true) {
      log.d("Found bluetooth device with our service")
      log.d("DEVICE ID: ${result.device.address} NAME: ${result.device.name}")
      foundValidDevice = true
    }

    if (!foundValidDevice) {
      val gapBlocks = decodeGAPbytes(scanRecord.bytes)
      val backgroundBitmasks = gapFilterAppleOverflowBlocks(gapBlocks)

      //    1 2 3 4 5 6 7  8
      // 0x00000000000000 20 0000000000000000
      //                \/
      //             0010 0000

      for (bitmask in backgroundBitmasks) {
        val byte = serverData.appleBit / 8
        val bitIndex = 0b10000000 shr (serverData.appleBit % 8)
        if (bitmask[byte].and(bitIndex.toByte()) == 0x20.toByte()) {
          if (callback.shouldConnectAppleBackgroundDevice(result.device.address)) {
            log.d("Found background iOS device: ${result.device.address}")
            log.d("- with bitmask ${bitmask.toHex()}")
            foundValidDevice = true
          }
        }
      }
    }

    if (!foundValidDevice) return

    val conn = ClientConnection(context, log.logger, scope, result.device, serverData, this)
    connections[result.device.address] = conn

    log.d("Found bluetooth device: ${result.device.address}, at ${result.rssi} dBm")
  }

  fun stopScanning() {
    val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner ?: return

    scanning = false
    bluetoothLeScanner.stopScan(this)
  }

  override fun onScanFailed(errorCode: Int) {
    log.d("onScanFailed: $errorCode")
  }

  companion object {
    const val TAG = "BleScanner"

    data class GapBlock(val type: Byte, val data: ByteArray)

    fun decodeGAPbytes(bytes: ByteArray): List<GapBlock> {
      val blocks = mutableListOf<GapBlock>()
      var nextBlock = 0

      while (nextBlock < bytes.size) {
        val len = bytes[nextBlock]
        if (len == 0x00.toByte()) break

        val type = bytes[nextBlock + 1]
        if (type == 0x00.toByte()) break

        if (nextBlock + len + 1 > bytes.size) break
        val data = bytes.copyOfRange(nextBlock + 2, nextBlock + len + 1)
        blocks.add(GapBlock(type, data))

        nextBlock += len + 1
      }

      return blocks
    }

    fun gapFilterAppleOverflowBlocks(blocks: List<GapBlock>) =
      blocks
        .filter { it.type == 0xFF.toByte() } // vendor specific
        .filter { it.data.toHex().startsWith("4c0001") } // apple overflow area
        .map { it.data.copyOfRange(3, 19) }
  }

  override fun onDeviceFullyConnected(connection: ClientConnection) {
    log.d("client onDeviceFullyConnected")
    callback.onConnectedToServer(connection)
  }

  override fun onDeviceDisconnected(connection: ClientConnection) {
    log.d("client onDeviceDisconnected")
    callback.onDisconnectedFromServer(connection)
    connections.remove(connection.bluetoothDevice.address)
  }

  override fun messageReceived(connection: ClientConnection, data: Data) {
    callback.onClientReceivedPacket(connection, data)
  }
}

interface BleScannerCallback {
  fun onConnectedToServer(connection: ClientConnection)
  fun onDisconnectedFromServer(connection: ClientConnection)
  fun onClientReceivedPacket(connection: ClientConnection, data: Data)
  fun shouldConnectAppleBackgroundDevice(address: String): Boolean
}

fun ByteArray.toHex(): String =
  joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
