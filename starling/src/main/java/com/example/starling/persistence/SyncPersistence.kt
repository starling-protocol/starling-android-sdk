package com.example.starling.persistence

import android.content.Context
import android.util.Log
import com.example.starling.Contact
import java.io.File

object SyncPersistence {

    val TAG = "sync-persistence"

    fun syncDir(ctx: Context): File {
        val syncDir = File(ctx.filesDir, "sync")
        if (!syncDir.exists()) {
            if (!syncDir.mkdir()) {
                throw Error("failed to create sync directory")
            }
        }

        return syncDir
    }

    fun storeContactState(ctx: Context, contact: Contact, state: ByteArray) {
        val syncDir = this.syncDir(ctx)
        val contactFile = File(syncDir, contact.id)
        contactFile.writeBytes(state)

        Log.d(TAG, "contact state saved to disk: ${contactFile.path} (${state.size} bytes)")
    }

    fun deleteContactState(ctx: Context, contact: Contact) {
        val syncDir = this.syncDir(ctx)
        val contactFile = File(syncDir, contact.id)
        contactFile.delete()
    }

    fun deleteAllContactStates(ctx: Context) {
        val syncDir = this.syncDir(ctx)
        syncDir.deleteRecursively()
    }

    fun loadContactState(ctx: Context, contact: Contact): ByteArray {
        val syncDir = this.syncDir(ctx)
        val contactFile = File(syncDir, contact.id)

        val contactState = contactFile.readBytes()

        Log.d(TAG, "contact state loaded from disk: ${contactFile.path} (${contactState.size} bytes)")

        return contactState
    }

    fun loadAllContactStates(ctx: Context): Map<Contact, ByteArray> {
        val contactFiles = this.syncDir(ctx).listFiles()!!

        Log.d(TAG, "loading all contact states ${this.syncDir(ctx).path} (${contactFiles.size} files)")

        return contactFiles.associate { file ->
            val contact = Contact(file.name)
            contact to this.loadContactState(ctx, contact)
        }
    }
}