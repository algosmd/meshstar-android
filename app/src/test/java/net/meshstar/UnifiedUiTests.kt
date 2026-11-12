package net.meshstar

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.meshstar.box.*
import net.meshstar.data.*
import net.meshstar.link.Contact
import net.meshstar.rnslite.*
import net.meshstar.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class UnifiedUiTests {
    @Test fun autoPrioritizesAvailableFreePathsAndManualNeverFallsBack() {
        assertEquals("bluetooth", resolvePath(PathChoice.AUTO, true, true, true))
        assertEquals("internet", resolvePath(PathChoice.AUTO, false, true, true))
        assertEquals("satellite", resolvePath(PathChoice.AUTO, false, false, true))
        assertNull(resolvePath(PathChoice.AUTO, false, false, false))
        assertNull(resolvePath(PathChoice.LORA, true, true, true))
        assertNull(resolvePath(PathChoice.RELAY, true, true, true))
        assertNull(resolvePath(PathChoice.INTERNET, true, false, true))
        assertEquals("satellite", resolvePath(PathChoice.SATELLITE, true, true, true))
    }
    @Test fun directoryMergesOnlyExplicitFingerprintLinksAndSumsUnreadOnce() {
        val rns = UiPeer("rns:synthetic", "Same name", rns = "synthetic", unread = 2, paths = setOf(ChatTab.SATELLITE))
        val ble = UiPeer("ble:synthetic", "Same name", ble = "synthetic", bleFingerprint = "synthetic-key", nearby = true, unread = 1, paths = setOf(ChatTab.BLUETOOTH))
        assertEquals(2, unifiedPeers(listOf(rns), listOf(ble), emptyMap()).size)
        val linked = unifiedPeers(listOf(rns), listOf(ble), mapOf("synthetic-key" to "synthetic")).single()
        assertEquals(3, linked.unread)
        assertEquals(setOf(ChatTab.SATELLITE, ChatTab.BLUETOOTH), linked.paths)
        assertTrue(linked.nearby)
    }
    @Test fun keyPinsAndReadMarkersPersistWithoutChangingCryptographicIdentity() = runBlocking {
        val me = Identity(ByteArray(64) { (it + 1).toByte() }); val peer = Contact.of(Identity(ByteArray(64) { (it + 80).toByte() }), "Synthetic")
        val store = MemoryStore(); val engine = SatEngine(me, store, now = { Instant.ofEpochMilli(1000) })
        engine.addContact(peer.qr); engine.verifyContact(peer.address, true); engine.linkBluetooth("synthetic-ble-key", peer.address)
        engine.markRead(peer.address)
        val restored = SatEngine(me, store)
        assertEquals(peer.key, restored.state.value.verifiedKeys[peer.address])
        assertEquals(peer.address, restored.state.value.bluetoothLinks["synthetic-ble-key"])
        assertEquals(1000L, restored.state.value.readThrough[peer.address])
        restored.verifyContact(peer.address, false)
        assertTrue(restored.state.value.verifiedKeys.isEmpty())
        assertArrayEquals(me.publicKey, restored.identity.publicKey)
    }
    @Test fun build3HistoryMigratesWithoutInventingUnreadOrDroppingKeys() = runBlocking {
        val gson = Gson(); val json = """{"name":"Synthetic","contacts":[],"messages":[],"internet":""}"""
        val old = gson.fromJson(json, SatState::class.java)
        assertTrue(old.verifiedKeys.isEmpty()); assertTrue(old.bluetoothLinks.isEmpty()); assertFalse(old.readTrackingInitialized)
        val store = MemoryStore(old); val engine = SatEngine(Identity(ByteArray(64) { 9 }), store)
        engine.initializeReadTracking()
        assertTrue(store.load().readTrackingInitialized)
        assertEquals("Synthetic", store.load().name)
    }
    @Test fun boxOnlineFlagIsValidatedAndSavedOnSentMessageWithoutGrantingDelivery() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val me = Identity(ByteArray(64) { (it + 1).toByte() }); val other = Identity(ByteArray(64) { (it + 80).toByte() })
            val box = Identity(ByteArray(64) { (it + 150).toByte() })
            val url = server.url("/").toString().trimEnd('/')
            val config = BoxConfig(url, "synthetic-credential", box.lxmfAddress.hex())
            val store = MemoryStore(SatState(box = config, contacts = mutableListOf(Contact.of(other, "Synthetic"))))
            val engine = SatEngine(me, store)
            server.enqueue(MockResponse.Builder().body("""{"addr":"${config.address}","link":{"online":true}}""").build())
            engine.refreshBoxStatus(); assertTrue(engine.pointOnline.value)
            engine.send(other.lxmfAddress.hex(), "synthetic text", Route.SATELLITE)
            val message = engine.state.value.messages.single()
            assertTrue(message.viaPoint); assertEquals(Delivery.QUEUED, message.delivery)
            assertTrue(SatEngine(me, store).state.value.messages.single().viaPoint)
            server.enqueue(MockResponse.Builder().body("""{"addr":"${config.address}","link":{"online":false}}""").build())
            engine.refreshBoxStatus(); assertFalse(engine.pointOnline.value)
            assertTrue(engine.state.value.messages.single().viaPoint) // Historical path does not flicker with live status.
            server.enqueue(MockResponse.Builder().body("""{"addr":"${other.lxmfAddress.hex()}","link":{"online":true}}""").build())
            assertTrue(runCatching { engine.refreshBoxStatus() }.isFailure)
            assertFalse(engine.pointOnline.value)
        }
    }
}
