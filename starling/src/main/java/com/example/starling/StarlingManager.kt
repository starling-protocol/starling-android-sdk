package com.example.starling

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.example.starling.persistence.KeystoreContactsContainer
import com.example.starling.persistence.SharedSecret
import com.example.starling.persistence.SyncPersistence
import kotlinx.coroutines.runBlocking
import mobile.Protocol
import mobile.ProtocolOptions

class StarlingManager(
    internal val context: Context,
    internal val logger: Logger,
    internal val callback: StarlingCallback
) {
    companion object {
        const val TAG = "StarlingManager"
    }

    internal val proto: Protocol = run {
        val options = ProtocolOptions()
        options.enableSync = true
        Protocol(ProtocolDevice(this), KeystoreContactsContainer(), options)
    }

    internal var serviceBinder: StarlingServiceBinder? = null

    var advertising: Boolean = false
        private set

    private val log = Log(logger, TAG)

    /**
     * Requires the following permissions on API version >=31
     *   Manifest.permission.BLUETOOTH_ADVERTISE,
     *   Manifest.permission.BLUETOOTH_CONNECT,
     *   Manifest.permission.BLUETOOTH_SCAN,
     *
     * On older devices <=30, the following permissions are required instead
     *   Manifest.permission.BLUETOOTH
     *   Manifest.permission.BLUETOOTH_ADMIN
     *   Manifest.permission.ACCESS_FINE_LOCATION
     */
    fun startAdvertising(
        serviceUUID: String,
        characteristicUUID: String,
        appleBit: Double
    ) {
        log.d("startAdvertising called")

            val advertiserIntent = Intent(context, StarlingService::class.java)

            advertiserIntent.putExtra("serviceUUID", serviceUUID)
            advertiserIntent.putExtra("characteristicUUID", characteristicUUID)
            advertiserIntent.putExtra("appleBitIndex", appleBit.toInt())

            val advertiserService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(advertiserIntent)
            } else {
                context.startService(advertiserIntent)
            }

            if (advertiserService != null) {
                val bindSuccess =
                    context.bindService(
                        Intent(context, StarlingService::class.java),
                        serviceConnection,
                        0
                    )

                if (bindSuccess) {
                    log.d("Bound StarlingService successfully")
                } else {
                    log.w("Failed to bind advertising service")
                    stopAdvertising()
                }
            } else {
                log.w("Failed to start advertising service")
                stopAdvertising()
            }
    }

    fun stopAdvertising() {
        log.d("stopAdvertising called")

        runBlocking {
            serviceBinder?.stopAdvertising(this)
        }

        context.unbindService(serviceConnection)
        context.stopService(Intent(context, StarlingService::class.java))

        advertising = false
        callback.advertisingEnded("stopped")
    }

    fun loadPersistedState() {
        log.w("loadPersistedState called")

        val contactStates = SyncPersistence.loadAllContactStates(context)
        log.i("loading ${contactStates.count()} contact states")

        contactStates.forEach { entry ->
            this.proto.syncLoadState(entry.key.id, entry.value)
        }

        this.proto.loadPersistedState()
    }

    fun deletePersistedState() {
        log.w("deletePersistedState called")
        SyncPersistence.deleteAllContactStates(context)
    }

    fun broadcastRouteRequest() {
        log.w("broadcastRouteRequest called")
        this.proto.broadcastRouteRequest()
    }

    fun startLinkSession(): LinkingSession {
        log.d("startLinkSession called")
        val session = this.proto.linkingStart()
        return LinkingSession(session)
    }

    fun connectLinkSession(session: LinkingSession, remoteShare: ByteArray): Contact {
        log.d("connectLinkSession called")
        val contact = this.proto.linkingCreate(session.internalSession, remoteShare)!!
        return Contact(contact)
    }

    fun deleteContact(contact: Contact) {
        log.d("deleteContact called")
        this.proto.deleteContact(contact.id)
    }

    fun sendMessage(session: Session, message: ByteArray): MessageID {
        log.d("sendMessage called")
        val msgID = proto.sendMessage(session.id, message)
        return MessageID(msgID)
    }

    fun syncAddMessage(contact: Contact, message: ByteArray, attachedContact: Contact? = null) {
        log.d("syncAddMessage called")
        proto.syncAddMessage(contact.id, message, attachedContact?.id ?: "")
    }

    fun newGroup(): Contact {
        log.d("newGroup called")
        return Contact(proto.newGroup())
    }

    fun joinGroup(groupSecret: ByteArray): Contact {
        log.d("joinGroup called")
        val newContact = proto.joinGroup(groupSecret)
        return Contact(newContact)
    }

    fun groupContact(groupSecret: ByteArray): Contact {
        return SharedSecret(groupSecret).contact
    }

    internal inner class ServiceCallback : StarlingServiceCallback {
        override val logger = this@StarlingManager.log.logger

        override fun advertisingStarted() {
            advertising = true
            callback.advertisingStarted()
        }

        override fun deviceConnected(device: DeviceAddress) {
            proto.onConnection(device.address)
            callback.deviceConnected(device)
        }

        override fun deviceDisconnected(device: DeviceAddress) {
            proto.onDisconnection(device.address)
            callback.deviceDisconnected(device)
        }

        override fun packetReceived(device: DeviceAddress, packet: ByteArray) {
            proto.receivePacket(device.address, packet)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            serviceBinder = service as StarlingServiceBinder?
            serviceBinder!!.registerCallback(ServiceCallback())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
        }
    }

}