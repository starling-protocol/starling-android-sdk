package com.example.starling.persistence

import com.example.starling.Contact
import mobile.ContactsContainer


class KeystoreContactsContainer: ContactsContainer {

    val groups: MutableMap<Contact, SharedSecret> = mutableMapOf()
    val links: MutableMap<Contact, SharedSecret> = mutableMapOf()

    override fun allGroups(): String =
        groups.keys
            .map { it.id }
            .joinToString(";")

    override fun allLinks(): String =
        links.keys
            .map { it.id }
            .joinToString(";")

    override fun contactSecret(contactID: String?): ByteArray {
        val contact = Contact(contactID!!)

        val secret = groups.getOrElse(contact) {
            links.get(contact)
        }

        return secret?.secret ?: throw Error("unknown contact when getting secret")
    }

    override fun deleteContact(contactID: String?) {
        val contact = Contact(contactID!!)

        groups.remove(contact)
        links.remove(contact)
    }

    override fun joinGroup(groupSecret: ByteArray?): String {
        val secret = SharedSecret(groupSecret!!)
        val contact = secret.contact
        groups[contact] = secret
        return contact.id
    }

    override fun newLink(linkSecret: ByteArray?): String {
        val secret = SharedSecret(linkSecret!!)
        val contact = secret.contact
        links[contact] = secret
        return contact.id
    }
}