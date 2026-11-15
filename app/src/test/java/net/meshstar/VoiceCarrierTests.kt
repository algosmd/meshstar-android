package net.meshstar

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.meshstar.box.*
import net.meshstar.data.*
import net.meshstar.link.Contact
import net.meshstar.meshcore.*
import net.meshstar.rnslite.*
import net.meshstar.voice.VoiceInbox
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
class VoiceCarrierTests {
    private val me=Identity(ByteArray(64){(it+1).toByte()})
    private val peer=Identity(ByteArray(64){(it+80).toByte()})
    private val node=NodeConfiguration("synthetic","Synthetic",7,"07".repeat(16),"09".repeat(32))
    private fun store()=MemoryStore(SatState(node=node,contacts=mutableListOf(Contact.of(peer,"Synthetic"))))
    private fun voice(size:Int=2400)=VoiceFrame.frames(peer,me,42,ByteArray(size){it.toByte()})
    @Test fun multipartPersistsAcrossRestartAndOnlyCompleteNoteProducesLastProof()=runBlocking {
        val store=store();var engine=SatEngine(me,store);val frames=voice()
        engine.receiveRadio(frames.last().frame,Reception(1.0,0))
        assertTrue(engine.state.value.messages.isEmpty());assertTrue(engine.state.value.proofs.isEmpty())
        engine=SatEngine(me,store)
        for(f in frames.dropLast(1).reversed())engine.receiveRadio(f.frame,Reception(1.0,0))
        assertEquals(1,engine.state.value.messages.size)
        assertEquals(2400*2,engine.state.value.messages.single().voice!!.codec.length)
        val proof=Packet(engine.state.value.proofs.single().raw.unhex())
        assertTrue(VoiceFrame.validateProof(proof,frames.last().frame,me))
        assertFalse(VoiceFrame.validateProof(proof,frames.first().frame,me))
        engine.receiveRadio(frames.first().frame,Reception(1.0,0))
        assertEquals(1,engine.state.value.messages.size);assertEquals(1,engine.state.value.proofs.size)
    }
    @Test fun noProofAndUnknownSenderNeverGetAcknowledged() {
        val s=SatState(contacts=mutableListOf(Contact.of(peer,"Synthetic")))
        val frame=VoiceFrame.create(peer,me,5,ByteArray(12),noProof=true)
        VoiceInbox.consume(s,frame.frame,me,100,Route.INTERNET,"synthetic")
        assertTrue(s.messages.single().verified);assertTrue(s.proofs.isEmpty())
        val unknown=SatState()
        VoiceInbox.consume(unknown,frame.frame,me,100,Route.INTERNET,"synthetic")
        assertFalse(unknown.messages.single().verified);assertTrue(unknown.proofs.isEmpty())
        assertEquals(1,unknown.pendingVoice.size)
        val tampered=frame.frame.copyOf().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
        VoiceInbox.consume(s,tampered,me,100,Route.LORA,"synthetic")
        assertFalse(s.messages.last().verified);assertTrue(s.proofs.isEmpty())
    }
    @Test fun loraSubmissionAndLastFrameSignatureControlDelivery()=runBlocking {
        val engine=SatEngine(me,store())
        engine.sendVoice(peer.lxmfAddress.hex(),ByteArray(2400),Route.LORA)
        val note=engine.state.value.messages.single().voice!!;val sent=mutableListOf<ByteArray>()
        engine.flushRadio{sent.add(it)}
        assertEquals(note.frames.size,sent.size)
        assertEquals(Delivery.SENT,engine.state.value.messages.single().delivery)
        engine.receiveRadio(VoiceFrame.proof(sent.first(),peer).raw,Reception(2.0,0))
        assertEquals(Delivery.SENT,engine.state.value.messages.single().delivery)
        engine.receiveRadio(VoiceFrame.proof(sent.last(),me).raw,Reception(2.0,0))
        assertEquals(Delivery.SENT,engine.state.value.messages.single().delivery)
        engine.receiveRadio(VoiceFrame.proof(sent.last(),peer).raw,Reception(2.0,0))
        assertEquals(Delivery.DELIVERED,engine.state.value.messages.single().delivery)
        assertTrue(runCatching{engine.sendVoice(peer.lxmfAddress.hex(),ByteArray(12),Route.RELAY)}.isFailure)
    }
    @Test fun internetVoiceResumesAtFirstUnsubmittedPart()=runBlocking {
        MockWebServer().use { server->server.start()
            val store=store();val engine=SatEngine(me,store)
            engine.configureInternet(server.url("/").toString())
            engine.sendVoice(peer.lxmfAddress.hex(),ByteArray(2400),Route.INTERNET)
            server.enqueue(MockResponse.Builder().body("""{"id":1}""").build())
            server.enqueue(MockResponse.Builder().code(503).body("{}").build())
            assertTrue(runCatching{engine.flushOutgoing(Route.INTERNET)}.isFailure)
            assertEquals(1,engine.state.value.messages.single().voice!!.submittedParts)
            val reopened=SatEngine(me,store)
            repeat(2){server.enqueue(MockResponse.Builder().body("""{"id":${it+2}}""").build())}
            reopened.flushOutgoing(Route.INTERNET)
            assertTrue(reopened.state.value.messages.single().submitted)
            assertEquals(Delivery.SENT,reopened.state.value.messages.single().delivery)
            val bodies=(0 until 4).map{server.takeRequest().body!!.utf8()}
            assertTrue(bodies.all{it.contains("\"kind\":\"voice\"")})
            assertEquals(bodies[1],bodies[2])
            assertNotEquals(bodies[0],bodies[2])
        }
    }
    @Test fun boxVoiceUsesVoiceEndpointAndNoUnsignedReceiptGrantsProof()=runBlocking {
        MockWebServer().use { server->server.start()
            val store=MemoryStore(SatState(box=BoxConfig(server.url("/").toString(),"synthetic","08".repeat(16)),contacts=mutableListOf(Contact.of(peer,"Synthetic"))))
            val engine=SatEngine(me,store)
            engine.sendVoice(peer.lxmfAddress.hex(),ByteArray(12),Route.SATELLITE)
            val raw=engine.state.value.messages.single().raw.unhex()
            server.enqueue(MockResponse.Builder().body("""{"id":1,"file":"synthetic-frame.bin","bytes":${raw.size},"dest":"${peer.lxmfAddress.hex()}"}""").build())
            engine.flushOutgoing(Route.SATELLITE)
            val request=server.takeRequest()
            assertEquals("/api/rns/voice",request.url.encodedPath)
            assertEquals("application/octet-stream",request.headers["Content-Type"])
            assertArrayEquals(raw,request.body!!.toByteArray())
            assertEquals(Delivery.SENT,engine.state.value.messages.single().delivery)
        }
    }
    @Test fun failedStorageDoesNotPublishOrConfirmIncomingVoice()=runBlocking {
        val store=store();val engine=SatEngine(me,store);store.fail=true
        assertTrue(runCatching{engine.receiveRadio(voice(12).single().frame,Reception(2.0,0))}.isFailure)
        assertTrue(engine.state.value.messages.isEmpty());assertTrue(engine.state.value.proofs.isEmpty())
    }
}
