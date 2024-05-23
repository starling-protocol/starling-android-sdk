package com.example.starling

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.app.ServiceCompat
import androidx.core.util.Consumer
import com.example.starling.connection.BleScanner
import com.example.starling.connection.BleScannerCallback
import com.example.starling.connection.ConnectionTable
import com.example.starling.connection.ServerData
import com.example.starling.connection.ServerManager
import com.example.starling.connection.ServerManagerCallback
import com.example.starling.connection.client.ClientConnection
import com.example.starling.connection.server.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

@SuppressLint("MissingPermission")
internal class StarlingService() : Service(), ServerManagerCallback, BleScannerCallback {
  companion object {
    private const val TAG = "StarlingService"
  }

  private var bluetoothObserver: BroadcastReceiver? = null
  private var serverManager: ServerManager? = null
  private var bleScanner: BleScanner? = null

  val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val log = Log(object : Logger {
    override fun log(priority: Int, tag: String, message: String) {
      callbackAction { it.logger.log(priority, tag, message) }
    }

    override fun logException(tag: String, message: String, exception: Exception) {
      callbackAction { it.logger.logException(tag, message, exception) }
    }
  }, TAG)

  private var connections = ConnectionTable(scope, log.logger)
  private var appleBackgroundDevices = mutableMapOf<String, Long>()

  private lateinit var serverData: ServerData

  private var eventQueue: MutableList<Consumer<StarlingServiceCallback>> = mutableListOf()
  private var callback: StarlingServiceCallback? = null

  private var bleAdvertiseCallback: AdvertiseCallback? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    log.d("starting starling service")

    val serviceUUIDString = intent!!.getStringExtra("serviceUUID")!!
    val serviceUUID = UUID.fromString(serviceUUIDString)

    val characteristicIDString = intent.getStringExtra("characteristicUUID")!!
    val characteristicUUID = UUID.fromString(characteristicIDString)

    val appleBitIndex = intent.getIntExtra("appleBitIndex", -1)
    assert(appleBitIndex >= 0)

    serverData = ServerData(serviceUUID, characteristicUUID, appleBitIndex)

    startForegroundNotification()
    bluetoothObserver = makeBluetoothObserver { enabled ->
      if (!enabled) {
        stopSelf(startId)
      }
    }

    startAdvertising()

    return START_REDELIVER_INTENT
  }

  override fun onDestroy() {
    log.d("stopping starling service")

    unregisterReceiver(bluetoothObserver)
    //stopAdvertising()

    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder {
    log.d("binding starling service")
    return StarlingServiceBinder(this)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    log.d("unbinding starling service")
    callback = null
    return super.onUnbind(intent)
  }

  private fun startAdvertising() {
    log.i("starting advertising")

    serverManager = ServerManager(this, log.logger, scope, serverData, this)
    if (!serverManager!!.open()) {
      log.e("failed to start server manager")
      stopSelf()
    }

    val bleAdvertiser = BleAdvertiser()
    bleAdvertiseCallback = bleAdvertiser.Callback()
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter.bluetoothLeAdvertiser?.startAdvertising(
      bleAdvertiser.settings(),
      bleAdvertiser.advertiseData(),
      bleAdvertiseCallback!!
    )

    bleScanner = BleScanner(this, log.logger, serverData, scope, this)
    bleScanner!!.startScanning()

    callbackAction { it.advertisingStarted() }
  }

  suspend fun stopAdvertising(scope: CoroutineScope) {
    log.i("stopping advertising")

    bleAdvertiseCallback?.let {
      val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
      bluetoothManager.adapter.bluetoothLeAdvertiser?.stopAdvertising(it)
      bleAdvertiseCallback = null
    }

    val handles = mutableListOf<Deferred<*>>()
    for (connection in connections) {
      handles.add(scope.async {
        connection.disconnect(scope)
        callback?.deviceDisconnected(connection.address)
      })
    }

    for (handle in handles) {
      handle.await()
    }

    serverManager?.close()
    serverManager = null

    bleScanner?.stopScanning()
    bleScanner = null
  }


  fun registerCallback(callback: StarlingServiceCallback) {
    this.callback = callback

    log.d("registering callback to starling service, sending ${eventQueue.size} queued events")
    for (event in eventQueue) {
      event.accept(callback)
    }
  }

  private fun callbackAction(event: Consumer<StarlingServiceCallback>) {
    this.callback?.let {
      event.accept(it)
    } ?: run {
      eventQueue.add(event)
    }
  }

  inner class BleAdvertiser {
    inner class Callback : AdvertiseCallback() {
      override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
        log.i("LE Advertise Started.")
      }

      override fun onStartFailure(errorCode: Int) {
        log.w("LE Advertise Failed: $errorCode")
        this@StarlingService.stopSelf()
      }
    }

    fun settings(): AdvertiseSettings {
      return AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
        .setConnectable(true)
        .setTimeout(0)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
        .build()
    }

    fun advertiseData(): AdvertiseData {
      return AdvertiseData.Builder()
        .setIncludeDeviceName(false) // Including it will blow the length
        .setIncludeTxPowerLevel(false)
        .addServiceUuid(ParcelUuid(serverData.serviceUUID))
        .build()
    }
  }

  override fun onDeviceConnectedToServer(connection: ServerConnection) {
    log.d("event onDeviceConnectedToServer: ${connection.bluetoothDevice.address}")
    if (connections.connectServer(connection)) {
      callbackAction {
        it.deviceConnected(connection.address)
      }
    }
  }

  override fun onDeviceDisconnectedFromServer(connection: ServerConnection) {
    log.d("event onDeviceDisconnectedFromServer: ${connection.bluetoothDevice.address}")
    if (connections.disconnectServer(connection)) {
      callbackAction {
        it.deviceDisconnected(connection.address)
      }
    }
  }

  override fun onServerReceivedPacket(connection: ServerConnection, data: Data) {
    log.d("event onServerReceivedPacket")
    val address = connection.address

    val packet = data.value ?: run {
      log.d("BLE Packet has not data")
      return
    }

    callbackAction {
      it.packetReceived(address, packet)
    }
  }

  override fun onConnectedToServer(connection: ClientConnection) {
    log.d("event OnConnectedToServer: ${connection.bluetoothDevice.address}")
    if (connections.connectClient(connection)) {
      callbackAction {
        it.deviceConnected(connection.address)
      }

    }
  }

  override fun onDisconnectedFromServer(connection: ClientConnection) {
    log.d("event onDisconnectedFromServer: ${connection.bluetoothDevice.address}")
    if (connections.disconnectClient(connection)) {
      callbackAction {
        it.deviceDisconnected(connection.address)
      }
    }
  }

  override fun onClientReceivedPacket(connection: ClientConnection, data: Data) {
    log.d("event onClientReceivedPacket")
    val address = connection.address

    val packet = data.value ?: run {
      log.d("BLE Packet has not data")
      return
    }

    callbackAction {
      it.packetReceived(address, packet)
    }
  }

  override fun shouldConnectAppleBackgroundDevice(address: String): Boolean {
    return appleBackgroundDevices[address]?.let { time ->
      val now = System.currentTimeMillis()
      val shouldConnect = (now - time) > 5 * 60 * 1000

      if (shouldConnect) {
        appleBackgroundDevices[address] = System.currentTimeMillis()
      }

      return@let shouldConnect
    } ?: run {
      appleBackgroundDevices[address] = System.currentTimeMillis()
      return@run true
    }
  }

  fun maxPacketSize(address: DeviceAddress): Int? {
    return connections.get(address)?.maximumWriteLength
  }

  fun sendPacket(address: DeviceAddress, packet: ByteArray) {
    connections.sendPacket(address, packet)
  }
}

private fun StarlingService.startForegroundNotification() {
  val notificationChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    NotificationChannel(
      StarlingService::class.java.simpleName,
      "Starling",
      NotificationManager.IMPORTANCE_DEFAULT
    )
  } else TODO("VERSION.SDK_INT < Oreo")

  val notificationService =
    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  notificationService.createNotificationChannel(notificationChannel)

  val notification = NotificationCompat.Builder(this, StarlingService::class.java.simpleName)
    //.setSmallIcon(R.mipmap.ic_launcher)
    .setContentTitle("Starling is advertising")
    .setContentText("3 peers, 1 connection")
//    .setOngoing(true)
    .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)

  val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
  } else {
    0
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(1, notification.build(), serviceType)
  } else {
    startForeground(1, notification.build())
  }
}

private fun StarlingService.makeBluetoothObserver(onChange: (state: Boolean) -> Unit): BroadcastReceiver {
  val bluetoothObserver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        BluetoothAdapter.ACTION_STATE_CHANGED -> {
          val bluetoothState = intent.getIntExtra(
            BluetoothAdapter.EXTRA_STATE,
            -1
          )
          when (bluetoothState) {
            BluetoothAdapter.STATE_ON -> onChange(true)
            BluetoothAdapter.STATE_OFF -> onChange(false)
          }
        }
      }
    }
  }
  registerReceiver(bluetoothObserver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

  return bluetoothObserver
}
