package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.meshstar.rnslite.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionTest {
    private val secret=ByteArray(16){7}
    @Test fun channelCollisionNeedsExplicitConsentAndKey() {
        val occupied=Channel(0,"Public",ByteArray(16){9})
        try { Protocol.choose(listOf(occupied),0,secret,false);fail() } catch(e:NodeError){assertEquals(NodeError.Code.OCCUPIED,e.code)}
        val replacement=Protocol.choose(listOf(occupied),0,secret,true)
        assertTrue(replacement.second);assertEquals("MESHSAT",replacement.first.name)
        assertTrue(occupied.secret.all{it==9.toByte()})
        try{Protocol.choose(listOf(occupied),0,null,true);fail()}catch(e:NodeError){assertEquals(NodeError.Code.SECRET,e.code)}
    }
    @Test fun existingTransportCanBeDiscoveredInAnotherSlot() {
        val (chosen,write)=Protocol.choose(listOf(Channel(0,"Public",secret),Channel(1,"MESHSAT",secret)),0,null,false)
        assertEquals(1,chosen.index);assertFalse(write)
    }
    @Test fun datagramFrameIsAtomicAndDoesNotContainPathBytes() {
        val frame=byteArrayOf(0x1b,(-12).toByte(),0,0,7,2,-1,-1,4,1,2,3,4)
        val d=Protocol.datagram(frame)
        assertEquals(-3.0,d.reception.snr,0.0);assertEquals(2,d.reception.hops)
        assertArrayEquals(byteArrayOf(1,2,3,4),d.bytes)
        assertArrayEquals(byteArrayOf(0x3e,7,-1,-1,-1,1,2,3,4),Protocol.sendData(d.bytes,7))
        try{Protocol.datagram(frame+0);fail()}catch(_:NodeError){}
    }
    @Test fun ordinaryChannelAndDirectMessagesArePreserved() {
        val channel=Protocol.foreign(byteArrayOf(0x11,8,0,0,2,-1,0)+little(100)+"Synthetic: hello".toByteArray())
        assertEquals(2,channel.channel);assertEquals("Synthetic: hello",channel.text);assertEquals(100L,channel.timestamp)
        val direct=Protocol.foreign(byteArrayOf(7)+ByteArray(6){4}+byteArrayOf(-1,0)+little(100)+"hello".toByteArray())
        assertNull(direct.channel);assertArrayEquals(ByteArray(6){4},direct.prefix)
    }
    @Test fun unrelatedPushesCannotCompletePendingCommand()= runTest {
        var waiting=0;var writes=0
        val q=CommandQueue({writes++},timeout=1000);q.onPush={waiting++};q.open()
        val result=async{q.command(byteArrayOf(0x1f,7),setOf(0x12),7)}
        runCurrent();q.receive(byteArrayOf(0x83.toByte()));q.receive(byteArrayOf(0x12,6));runCurrent()
        assertFalse(result.isCompleted);assertEquals(1,waiting);assertEquals(1,writes)
        q.receive(byteArrayOf(0x12,7));assertArrayEquals(byteArrayOf(0x12,7),result.await())
    }
    @Test fun timeoutPoisonsLateOkAndRequiresReconnect()=runTest {
        var poisoned=0;val q=CommandQueue({},timeout=100,desynchronized={poisoned++});q.open()
        val attempt=async{runCatching{q.command(byteArrayOf(1),setOf(0))}}
        advanceTimeBy(101);runCurrent();assertTrue(attempt.await().exceptionOrNull() is NodeError)
        q.receive(byteArrayOf(0));assertEquals(1,poisoned)
        try{q.command(byteArrayOf(1),setOf(0));fail()}catch(e:NodeError){assertEquals(NodeError.Code.OFFLINE,e.code)}
    }
    @Test fun contactsStreamIgnoresPushAndDoesNotRetainLocationFields()=runTest {
        val q=CommandQueue({});q.open()
        val result=async{q.contacts()};runCurrent()
        q.receive(byteArrayOf(2)+little(1));q.receive(byteArrayOf(0x83.toByte()))
        val contact=ByteArray(148);contact[0]=3;ByteArray(32){6}.copyInto(contact,1);"Synthetic".toByteArray().copyInto(contact,100);little(10).copyInto(contact,144)
        q.receive(contact);q.receive(byteArrayOf(4)+little(10))
        val (list,cursor)=result.await();assertEquals(1,list.size);assertEquals("Synthetic",list.single().name);assertEquals(10,cursor)
        assertFalse(NodeContact::class.java.declaredFields.any{it.name in setOf("latitude","longitude","raw")})
    }
    @Test fun contactProgressExtendsInactivityDeadline()=runTest {
        val q=CommandQueue({},timeout=1000);q.open()
        val result=async{q.contacts()};runCurrent();q.receive(byteArrayOf(2)+little(4))
        repeat(4){n->advanceTimeBy(700);runCurrent()
            val b=ByteArray(148);b[0]=3;b[1]=(n+1).toByte();little(10).copyInto(b,144);q.receive(b)
        }
        q.receive(byteArrayOf(4)+little(10));assertEquals(4,result.await().first.size)
    }
    @Test fun fragmentationHasGlobalPacingAcrossPacketsAndProofs()=runTest {
        lateinit var session:Session
        var clock=0L;val sent=mutableListOf<Pair<Long,ByteArray>>()
        session=Session(this,write={frame->
            launch {
                when(frame[0].toInt() and 255){
                    1->session.queue.receive(ByteArray(58).also{it[0]=5})
                    0x16->session.queue.receive(byteArrayOf(0x0d,3,0,1,0,0,0,0))
                    0x1f->session.queue.receive(byteArrayOf(0x12,0)+"MESHSAT".toByteArray().copyOf(32)+secret)
                    4->{session.queue.receive(byteArrayOf(2)+little(0));session.queue.receive(byteArrayOf(4)+little(0))}
                    10->session.queue.receive(byteArrayOf(10))
                    0x3e->{sent.add(clock to frame);session.queue.receive(byteArrayOf(0))}
                }
            }
        },disconnect={},save={},packet={_,_->},foreign={},now={clock},pause={clock+=it})
        session.initialize(NodeConfiguration("synthetic","Synthetic",0));runCurrent()
        val raw=ByteArray(400){it.toByte()};session.send(raw);session.send(ByteArray(83){1})
        assertEquals(4,sent.size)
        assertTrue(sent.zipWithNext().all{(a,b)->b.first-a.first>=1500})
        val assembler=MeshCoreFrag.Assembler()
        var joined:ByteArray?=null
        sent.take(3).forEach{joined=assembler.feed(it.second.copyOfRange(5,it.second.size))?:joined}
        assertArrayEquals(raw,joined);session.stop()
    }
}
