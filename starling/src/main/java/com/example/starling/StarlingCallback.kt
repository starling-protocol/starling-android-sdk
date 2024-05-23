package com.example.starling

interface StarlingCallback {
    fun advertisingStarted()
    fun advertisingEnded(reason: String)

    fun deviceConnected(deviceAddress: DeviceAddress)
    fun deviceDisconnected(deviceAddress: DeviceAddress)
    fun messageReceived(session: Session, message: ByteArray)

    fun messageDelivered(messageID: MessageID)
    fun sessionBroken(session: Session)
    fun sessionEstablished(session: Session, contact: Contact, deviceAddress: DeviceAddress)
    fun sessionRequested(session: Session, contact: Contact): ByteArray?

    fun syncStateChanged(contact: Contact, stateUpdate: ByteArray)
}