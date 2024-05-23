package com.example.starling

import android.os.Binder
import kotlinx.coroutines.CoroutineScope

internal class StarlingServiceBinder(private val service: StarlingService) : Binder() {
    //fun getService(): StarlingService = service

    fun maxPacketSize(address: DeviceAddress): Int? {
        return service.maxPacketSize(address)
    }

    fun sendPacket(address: DeviceAddress, packet: ByteArray) {
        return service.sendPacket(address, packet)
    }

    fun registerCallback(callback: StarlingServiceCallback) {
        service.registerCallback(callback)
    }

    suspend fun stopAdvertising(scope: CoroutineScope) {
        service.stopAdvertising(scope)
    }
}

internal interface StarlingServiceCallback {
    val logger: Logger

    fun advertisingStarted()
    fun deviceConnected(device: DeviceAddress)
    fun deviceDisconnected(device: DeviceAddress)
    fun packetReceived(device: DeviceAddress, packet: ByteArray)
}