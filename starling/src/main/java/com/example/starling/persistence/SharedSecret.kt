package com.example.starling.persistence

import android.util.Base64
import com.example.starling.Contact
import java.security.MessageDigest

data class SharedSecret(val secret: ByteArray) {
    val contact: Contact
        get() {
            val sha256 = MessageDigest.getInstance("SHA-256")
            val digest = sha256.digest(secret)
            val contactID = Base64.encodeToString(digest, Base64.URL_SAFE)
            return Contact(contactID)
        }

}
