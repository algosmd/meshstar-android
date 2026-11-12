package net.meshstar

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.meshstar.box.*
import net.meshstar.data.*
import net.meshstar.link.Contact
import net.meshstar.meshcore.*
import net.meshstar.rnslite.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[34],application=android.app.Application::class,manifest=org.robolectric.annotation.Config.NONE)
class RadioCarrierTests {
    private val me=Identity(ByteArray(64){(it+1).toByte()})
    private val peer=Identity(ByteArray(64){(it+80).toByte()})
    private val node=NodeConfiguration("synthetic-device","Synthetic",7,"07".repeat(16),"09".repeat(32))
    @Test fun relayAddsTransportHeaderWithoutChangingProofHash()=runBlocking {
        val store=MemoryStore(SatState(node=node,box=BoxConfig("http://localhost","synthetic","08".repeat(16),"06".repeat(16)),contacts=mutableListOf(Contact.of(peer,"Synthetic"))))
        val engine=SatEngine(me,store)
        val hash=engine.send(peer.lxmfAddress.hex(),"synthetic",Route.RELAY)
        val sent=mutableListOf<ByteArray>();engine.flushRadio{sent.add(it)}
        val packet=Packet(sent.single())
        assertEquals(hash,packet.hash.hex());assertEquals("06".repeat(16),packet.transportId!!.hex())
        assertEquals(Delivery.SENT,engine.state.value.messages.single().delivery)
        engine.receiveRadio(packet.prove(peer).raw,Reception(2.0,1))
        assertEquals(Delivery.DELIVERED,engine.state.value.messages.single().delivery)
    }
    @Test fun radioReceivePersistsBeforeProofAndDeduplicatesAcrossHttp()=runBlocking {
        val store=MemoryStore(SatState(node=node,contacts=mutableListOf(Contact.of(peer,"Synthetic"))))
        val engine=SatEngine(me,store)
        val packet=LXMessage.create(peer,me,"hello".toByteArray(),10.0).packet(me)
        engine.receiveRadio(packet.raw,Reception(2.5,0));engine.receiveRadio(packet.raw,Reception(2.5,0))
        assertEquals(1,engine.state.value.messages.size);assertEquals(1,store.load().proofs.size)
        val sent=mutableListOf<ByteArray>();engine.flushRadio{sent.add(it)}
        assertTrue(packet.validateProof(Packet(sent.single()),me));assertTrue(engine.state.value.proofs.isEmpty())
        assertEquals(2.5,engine.state.value.messages.single().radioSnr!!,0.0)
    }
    @Test fun delayedReceiveKeepsOriginalNodeForItsProof()=runBlocking {
        val engine=SatEngine(me,MemoryStore(SatState(node=node,contacts=mutableListOf(Contact.of(peer,"Synthetic")))))
        val packet=LXMessage.create(peer,me,"hello".toByteArray(),10.0).packet(me)
        engine.saveNode(node.copy(publicKey="0a".repeat(32)))
        engine.receiveRadio(packet.raw,Reception(2.0,0),node.publicKey)
        assertEquals(node.publicKey,engine.state.value.proofs.single().carrier)
        val sent=mutableListOf<ByteArray>();engine.flushRadio{sent.add(it)}
        assertTrue(sent.isEmpty())
        engine.saveNode(node);engine.flushRadio{sent.add(it)}
        assertTrue(packet.validateProof(Packet(sent.single()),me))
    }
    @Test fun viaPointProofCannotEstablishItsOwnDirectRadioEvidence()=runBlocking {
        MockWebServer().use{server->server.start()
            val box=BoxConfig(server.url("/").toString().trimEnd('/'),"synthetic","08".repeat(16))
            val store=MemoryStore(SatState(node=node,box=box,contacts=mutableListOf(Contact.of(peer,"Synthetic"))))
            val engine=SatEngine(me,store,now={Instant.ofEpochSecond(100)})
            server.enqueue(MockResponse.Builder().body("""{"addr":"${box.address}","link":{"online":true}}""").build())
            engine.refreshBoxStatus();engine.send(peer.lxmfAddress.hex(),"hello",Route.LORA)
            val packet=Packet(engine.state.value.messages.single().raw.unhex())
            engine.receiveRadio(packet.prove(peer).raw,Reception(2.0,0))
            assertTrue(engine.state.value.messages.single().viaPoint)
            assertTrue(engine.state.value.radioObservations[peer.lxmfAddress.hex()].isNullOrEmpty())
        }
    }
    @Test fun unknownNativeSenderIsPreservedAndPrefixCollisionsStayUnresolved()=runBlocking {
        val engine=SatEngine(me,MemoryStore(SatState(node=node)))
        val key=ByteArray(32){4};val prefix=key.copyOf(6)
        engine.receiveForeign(ForeignText(null,prefix,10,"hello",Reception(1.0,0)))
        assertEquals("direct:"+prefix.hex(),engine.state.value.channelTexts.single().channel)
        engine.saveNativeContacts(listOf(NodeContact(key,"Synthetic",1,0,0)))
        assertEquals(key.hex(),engine.state.value.channelTexts.single().directKey)
        val collision=key.copyOf().also{it[31]=5}
        engine.saveNativeContacts(listOf(NodeContact(key,"One",1,0,0),NodeContact(collision,"Two",1,0,0)))
        engine.receiveForeign(ForeignText(null,prefix,11,"second",Reception(1.0,0)))
        assertNull(engine.state.value.channelTexts.last().directKey)
        assertEquals("direct:"+prefix.hex(),engine.state.value.channelTexts.last().channel)
    }
    @Test fun channelHistoryMatchesNameAndTimestampRatherThanSlot()=runBlocking {
        MockWebServer().use{server->server.start()
            val box=BoxConfig(server.url("/").toString().trimEnd('/'),"synthetic","08".repeat(16))
            val engine=SatEngine(me,MemoryStore(SatState(box=box,node=node)))
            engine.saveChannels(listOf(Channel(2,"Public",byteArrayOf())))
            engine.receiveForeign(ForeignText(2,null,30,"Synthetic: hello",Reception(2.0,1)))
            server.enqueue(MockResponse.Builder().body("""[{"idx":0,"name":"Public"},{"idx":7,"name":"MESHSAT"}]""").build())
            server.enqueue(MockResponse.Builder().body("""[{"ts":35.1,"sender_timestamp":30,"text":"Synthetic: hello","snr":2,"path_len":1},{"ts":36.2,"sender_timestamp":31,"text":"Synthetic: second","snr":1,"path_len":0}]""").build())
            engine.pollChannelHistory()
            assertEquals(2,engine.state.value.channelTexts.size)
            assertEquals(1,engine.state.value.channelTexts.count{it.fromBox})
            assertTrue(engine.state.value.channelTexts.all{it.channel=="Public"})
            assertEquals(2,server.requestCount)
        }
    }
}
