package net.meshstar

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.meshstar.box.*
import net.meshstar.data.*
import net.meshstar.link.Contact
import net.meshstar.mailbox.MailboxTransport
import net.meshstar.rnslite.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Base64

class MemoryStore(initial: SatState = SatState()) : StateStore {
    private val gson = Gson()
    private var encoded = gson.toJson(initial)
    var fail = false
    override fun load(): SatState = gson.fromJson(encoded, SatState::class.java)
    override fun save(state: SatState) { check(!fail) { "simulated storage failure" }; encoded = gson.toJson(state) }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class CarrierTests {
    private val alice = Identity(ByteArray(64) { (it + 1).toByte() })
    private val bob = Identity(ByteArray(64) { (it + 80).toByte() })
    private val bridge = Identity(ByteArray(64) { (it + 150).toByte() })
    private val instant = Instant.parse("2026-11-04T01:00:00Z")
    private fun response(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).build()
    private fun receipt(packet: Packet) = """{"id":1,"hash":"${packet.hash.hex()}","dest":"${packet.destination.hex()}"}"""
    private fun base(server: MockWebServer) = server.url("/").toString().trimEnd('/')
    private fun store(server: MockWebServer, box: Boolean = false) = MemoryStore(SatState(
        box = if (box) BoxConfig(base(server), "synthetic-credential", bridge.lxmfAddress.hex()) else null,
        internet = if (box) "" else base(server), contacts = mutableListOf(Contact.of(bob, "Peer"))))
    private fun pickup(packet: Packet, mailbox: String, id: Long = 1) = """{"items":[{"id":$id,"mailbox":"$mailbox","kind":"packet","data":"${Base64.getEncoder().encodeToString(packet.raw)}","ts":1}],"next":$id}"""
    private fun inbox(packet: Packet, id: Long = 1) = """[{"id":$id,"kind":"data","raw":"${packet.raw.hex()}"}]"""

    @Test fun resetRejectsLateWritesAndEmptyServerIsDisabled(): Unit = runBlocking {
        val store = MemoryStore(); val engine = SatEngine(alice, store)
        assertEquals("", engine.state.value.internet)
        engine.closeForReset()
        assertTrue(runCatching { engine.addContact(Contact.of(bob, "Peer").qr) }.isFailure)
        assertTrue(store.load().contacts.isEmpty())
    }

    @Test fun contactsRoundTripLegacyAndCurrentLinks() {
        val contact = Contact.of(alice, "Test : + / имя")
        assertEquals(contact, Contact.parse(contact.qr))
        assertEquals(contact, Contact.parse(contact.link))
        assertEquals(contact, Contact.parse(contact.link.replace("meshstar:", "meshsat:")))
        assertTrue(contact.link.startsWith("meshstar://"))
        assertEquals(16, contact.identity.lxmfAddress.size)
        assertThrows(IllegalArgumentException::class.java) { Contact.parse(contact.qr.replace(contact.address, bob.lxmfAddress.hex())) }
        assertThrows(IllegalArgumentException::class.java) { Contact.parse(contact.link + "&qr=other") }
    }

    @Test fun boxPairRegistersCanonicalIosQr() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = MemoryStore(); val engine = SatEngine(alice, store)
            server.enqueue(response("""{"addr":"${bridge.lxmfAddress.hex()}"}"""))
            server.enqueue(response(JsonObject().apply { addProperty("qr", Contact.of(bridge, "Bridge").qr) }.toString()))
            server.enqueue(response("{}"))
            engine.configureBox(base(server), "synthetic-credential")
            assertEquals(bridge.lxmfAddress.hex(), engine.state.value.box!!.address)
            assertEquals("/api/status", server.takeRequest().target)
            assertEquals("/api/pair", server.takeRequest().target)
            val register = server.takeRequest()
            assertEquals("synthetic-credential", register.headers["X-Box-Token"])
            assertEquals(engine.myContact().qr, JsonParser.parseString(register.body!!.utf8()).asJsonObject["qr"].asString)
        }
    }

    @Test fun serverDeliveredIsNotAProofAndSignedProofSurvivesRestart() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server, true); var engine = SatEngine(alice, store)
            val hash = engine.send(bob.lxmfAddress.hex(), "hello", Route.SATELLITE)
            val packet = Packet(engine.state.value.messages.single().raw.unhex())
            assertEquals(Delivery.QUEUED, engine.state.value.messages.single().delivery)
            server.enqueue(response(receipt(packet))); engine.flushOutgoing(Route.SATELLITE); server.takeRequest()
            server.enqueue(response("""[{"id":1,"pkt_hash":"$hash","status":"delivered"}]""")); server.enqueue(response("[]"))
            engine.pollBox()
            assertEquals(Delivery.SENT, engine.state.value.messages.single().delivery)
            server.takeRequest(); server.takeRequest()
            engine = SatEngine(alice, store)
            server.enqueue(response("[]")); server.enqueue(response(inbox(packet.prove(bob))))
            engine.pollBox()
            assertEquals(Delivery.DELIVERED, engine.state.value.messages.single().delivery)
            assertTrue(server.takeRequest().target.contains("since=0")) // Revisit pending IDs.
            server.takeRequest()

        }
    }

    @Test fun invalidConfirmationCannotMarkDelivered() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val engine = SatEngine(alice, store(server, true))
            engine.send(bob.lxmfAddress.hex(), "hello", Route.SATELLITE)
            val packet = Packet(engine.state.value.messages.single().raw.unhex())
            server.enqueue(response("[]")); server.enqueue(response(inbox(packet.prove(bridge))))
            engine.pollBox(); assertEquals(Delivery.QUEUED, engine.state.value.messages.single().delivery)
        }
    }

    @Test fun receivePersistsAndConfirmsWithoutDuplicateMessages() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server, true); var engine = SatEngine(alice, store)
            val packet = LXMessage.create(bob, alice, "reply".toByteArray(), 1.0).packet(alice)
            repeat(2) { index ->
                server.enqueue(response("[]")); server.enqueue(response(inbox(packet, index + 1L)))
                server.enqueue(response(receipt(packet.prove(alice))))
                engine.pollBox()
                server.takeRequest(); server.takeRequest()
                val proof = server.takeRequest()
                assertTrue(proof.target.contains("no_proof=true"))
                assertTrue(packet.validateProof(Packet(proof.body!!.toByteArray()), alice))
                engine = SatEngine(alice, store)
            }
            assertEquals(1, engine.state.value.messages.size); assertTrue(engine.state.value.messages.single().verified)
        }
    }

    @Test fun unknownSenderIsVisibleButNeverConfirmed() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val engine = SatEngine(alice, store(server, true))
            val packet = LXMessage.create(bridge, alice, "untrusted".toByteArray(), 1.0).packet(alice)
            server.enqueue(response("[]")); server.enqueue(response(inbox(packet)))
            engine.pollBox()
            assertFalse(engine.state.value.messages.single().verified); assertTrue(engine.state.value.proofs.isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun urlChangeForSameBoxKeepsCursorsAndMatchesPacketHash() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val initial = store(server, true).load()
            initial.boxCursors[bridge.lxmfAddress.hex()] = BoxCursor(42, 9)
            val engine = SatEngine(alice, MemoryStore(initial))
            server.enqueue(response("""{"addr":"${bridge.lxmfAddress.hex()}"}"""))
            server.enqueue(response(JsonObject().apply { addProperty("qr", Contact.of(bridge, "Bridge").qr) }.toString()))
            server.enqueue(response("{}"))
            engine.configureBox(base(server) + "/alternate", "synthetic-credential")
            assertEquals(BoxCursor(42, 9), engine.state.value.boxCursors[bridge.lxmfAddress.hex()])
        }
    }

    @Test fun mailboxDropUsesOneValidPowChallengeAndNoBoxHeader() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val raw = byteArrayOf(1,2,3); val address = ByteArray(16) { 7 }
            server.enqueue(response("""{"detail":{"error":"stamp required","pow_bits":8}}""", 429))
            server.enqueue(response("""{"id":2,"dup":false}"""))
            assertEquals(2L, MailboxTransport(base(server)).drop(raw, address))
            server.takeRequest(); val retry = server.takeRequest()
            val body = JsonParser.parseString(retry.body!!.utf8()).asJsonObject
            assertTrue(MailboxStamp.verify(address, raw, body["stamp"].asString.unhex(), 8))
            assertNull(retry.headers["X-Box-Token"]); assertEquals("/v1/drop", retry.target)
        }
    }

    @Test fun mailboxRejectsUnboundedWorkAndOrdinaryRateLimitDoesNotMine() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(response("""{"detail":{"error":"stamp required","pow_bits":25}}""", 429))
            assertTrue(runCatching { MailboxTransport(base(server)).drop(byteArrayOf(1), ByteArray(16)) }.exceptionOrNull() is RNSError)
            server.enqueue(response("""{"detail":{"error":"rate limit"}}""", 429))
            assertTrue(runCatching { MailboxTransport(base(server)).drop(byteArrayOf(1), ByteArray(16)) }.exceptionOrNull() is HttpFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun mailboxReceiveProofThenAckAndPersistCursor() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server); val engine = SatEngine(alice, store, now = { instant })
            val packet = LXMessage.create(bob, alice, "internet".toByteArray(), 1.0).packet(alice)
            val address = Mailbox.pickupAddresses(alice, bob, instant)[1].hex()
            server.enqueue(response(pickup(packet, address)))
            server.enqueue(response("""{"id":2,"dup":false}""")); server.enqueue(response("""{"deleted":1}"""))
            engine.pollMailbox()
            val pick = JsonParser.parseString(server.takeRequest().body!!.utf8()).asJsonObject
            assertEquals(25, pick["wait"].asInt); assertEquals(3, pick.getAsJsonArray("mailboxes").size())
            assertEquals("/v1/drop", server.takeRequest().target)
            val ack = server.takeRequest(); assertEquals("/v1/ack", ack.target)
            assertEquals(1L, engine.state.value.mailCursors.values.single())
            assertEquals("internet", store.load().messages.single().text)
            assertTrue(store.load().proofs.isEmpty())
        }
    }

    @Test fun failedProofLeavesCursorAndRetryRegeneratesBeforeAck() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server); var engine = SatEngine(alice, store, now = { instant })
            val packet = LXMessage.create(bob, alice, "durable".toByteArray(), 1.0).packet(alice)
            val address = Mailbox.pickupAddresses(alice, bob, instant)[1].hex()
            server.enqueue(response(pickup(packet, address))); server.enqueue(response("{}", 503))
            assertTrue(runCatching { engine.pollMailbox() }.isFailure)
            assertEquals(1, store.load().messages.size); assertEquals(1, store.load().proofs.size)
            assertTrue(store.load().mailCursors.isEmpty()); assertEquals(2, server.requestCount)
            engine = SatEngine(alice, store, now = { instant })
            server.enqueue(response(pickup(packet, address))); server.enqueue(response("""{"id":2,"dup":true}""")); server.enqueue(response("""{"deleted":1}"""))
            engine.pollMailbox()
            assertEquals(1, engine.state.value.messages.size); assertEquals(1L, store.load().mailCursors.values.single())
        }
    }

    @Test fun diskFailureNeverAcksOrAdvancesCursor() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server); val engine = SatEngine(alice, store, now = { instant })
            val packet = LXMessage.create(bob, alice, "durable".toByteArray(), 1.0).packet(alice)
            server.enqueue(response(pickup(packet, Mailbox.pickupAddresses(alice, bob, instant)[1].hex())))
            store.fail = true
            assertTrue(runCatching { engine.pollMailbox() }.isFailure)
            assertEquals(1, server.requestCount); assertTrue(engine.state.value.messages.isEmpty())
        }
    }

    @Test fun offlineOutboxRetriesSameCiphertextAfterRestart() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val store = store(server); var engine = SatEngine(alice, store, now = { instant })
            engine.send(bob.lxmfAddress.hex(), "offline", Route.INTERNET)
            server.enqueue(response("{}", 503))
            assertTrue(runCatching { engine.flushOutgoing(Route.INTERNET) }.isFailure)
            val first = server.takeRequest().body!!.utf8()
            engine = SatEngine(alice, store, now = { instant.plusSeconds(300) })
            server.enqueue(response("""{"id":1,"dup":true}""")); engine.flushOutgoing(Route.INTERNET)
            assertEquals(first, server.takeRequest().body!!.utf8())
            assertEquals(Delivery.SENT, engine.state.value.messages.single().delivery)
        }
    }

    @Test fun mailboxWrongRecipientOrCursorIsRejectedWithoutAck() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); val packet = LXMessage.create(bob, alice, "x".toByteArray(), 1.0).packet(alice)
            server.enqueue(response(pickup(packet, ByteArray(16) { 9 }.hex())))
            assertTrue(runCatching { MailboxTransport(base(server)).pickup(listOf(ByteArray(16).hex()), 0) }.isFailure)
            server.enqueue(response("""{"items":[],"next":99}"""))
            assertTrue(runCatching { MailboxTransport(base(server)).pickup(listOf(ByteArray(16).hex()), 0) }.isFailure)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun redirectsNeverForwardCredential() : Unit = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { target ->
            server.start(); target.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", target.url("/capture")).body("").build())
            val error = runCatching { CarrierHttp().request(base(server), "api/status", "synthetic-credential") }.exceptionOrNull()
            assertEquals(302, (error as HttpFailure).code); assertEquals(0, target.requestCount)
        } }
    }

    @Test fun longPollIsCancellable() : Unit = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse.Builder().body("{}").headersDelay(30, java.util.concurrent.TimeUnit.SECONDS).build())
            val job = launch(Dispatchers.IO) { MailboxTransport(base(server)).pickup(listOf(ByteArray(16).hex()), 0) }
            withContext(Dispatchers.IO) { server.takeRequest() }
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
}
