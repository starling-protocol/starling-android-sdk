package com.example.starling

import mobile.LinkingSession

data class DeviceAddress(val address: String)

data class Contact(val id: String)
data class MessageID(val id: Long)
data class Session(val id: Long)

class LinkingSession(internal val internalSession: LinkingSession) {
    val share: ByteArray = internalSession.share
}
