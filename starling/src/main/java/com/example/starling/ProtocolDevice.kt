package com.example.starling

import android.util.Log
import com.example.starling.persistence.SyncPersistence
import mobile.Device

internal class ProtocolDevice(val starling: StarlingManager) : Device {

    inner class ServiceNotBoundException: Exception("StarlingService not bound") {}

    inner class UnknownDeviceAddressException(val deviceAddress: DeviceAddress):
        Exception("Unknown device: $deviceAddress") {}

    fun getService() =
        starling.serviceBinder ?: throw ServiceNotBoundException()

    override fun log(message: String?) {
        starling.logger.log(Log.INFO, "PROTO", message!!)
    }

    override fun maxPacketSize(deviceAddress: String?): Long {
        val address = DeviceAddress(deviceAddress!!)
        return getService().maxPacketSize(address)?.toLong()
            ?: throw UnknownDeviceAddressException(address)
    }

    override fun messageDelivered(msgID: Long) {
        starling.callback.messageDelivered(MessageID(msgID))
    }

    override fun processMessage(session: Long, message: ByteArray?) {
        starling.callback.messageReceived(Session(session), message!!)
    }

    override fun sendPacket(address: String?, packet: ByteArray?) {
        getService().sendPacket(DeviceAddress(address!!), packet!!)
    }

    override fun sessionBroken(session: Long) {
        starling.callback.sessionBroken(Session(session))
    }

    override fun sessionEstablished(session: Long, contact: String?, address: String?) {
        starling.callback.sessionEstablished(Session(session), Contact(contact!!), DeviceAddress(address!!))
    }

    override fun sessionRequested(session: Long, contact: String?): ByteArray {
        return starling.callback.sessionRequested(Session(session), Contact(contact!!)) ?: ByteArray(0)
    }

    override fun syncStateChanged(contactID: String?, stateUpdate: ByteArray?) {
        val contact = Contact(contactID!!)

        // TODO: Re-enable when KeystoreContactContainer is finished
        //SyncPersistence.storeContactState(starling.context, contact, stateUpdate!!)

        return starling.callback.syncStateChanged(contact, stateUpdate!!)
    }
}