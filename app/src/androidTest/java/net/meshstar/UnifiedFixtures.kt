package net.meshstar

import net.meshstar.data.*
import net.meshstar.ui.*
import net.meshstar.link.Contact
import net.meshstar.rnslite.Identity

/** Entirely synthetic; never loads app storage, device contacts, location or transports. */
fun unifiedFixture(): UiSnapshot {
    val me = Contact.of(Identity(ByteArray(64) { (it + 1).toByte() }), "Alex")
    val peer = Contact.of(Identity(ByteArray(64) { (it + 80).toByte() }), "Alice")
    val alice = UiPeer("alice", "Alice", peer.fingerprint, rns = peer.address, ble = "synthetic-a", bleFingerprint = "synthetic-fingerprint",
        nearby = true, verified = false, preview = "Hello, MeshStar", unread = 2, paths = setOf(ChatTab.BLUETOOTH, ChatTab.LORA, ChatTab.SATELLITE), at = 120000, qr = peer.qr)
    val bob = UiPeer("bob", "Bob", "synthetic-fingerprint-b", ble = "synthetic-b", preview = "Hello", unread = 1, paths = setOf(ChatTab.BLUETOOTH), at = 60000)
    return UiSnapshot("Alex", listOf(alice, bob), mapOf("alice" to listOf(
        UiMessage("m1", "Alice", "Hello, MeshStar", 60000, false, Route.INTERNET, signature = true, peer = "alice"),
        UiMessage("m2", "Alex", "Hello", 120000, true, Route.SATELLITE, true, Delivery.DELIVERED, true, "alice"),
        UiMessage("m3", "Alice", "✓", 180000, false, Route.SATELLITE, true, Delivery.DELIVERED, true, "alice"))),
        listOf(UiMessage("p1", "Alice", "Hello", 60000, false, peer = "alice"), UiMessage("p2", "Alex", "Hello, MeshStar", 120000, true)),
        myQr = me.qr, myFingerprint = me.fingerprint, myAddress = me.address, pointOnline = true, boxStatus = "Box available",
        mailStatus = "Internet server available", bleReady = true)
}
