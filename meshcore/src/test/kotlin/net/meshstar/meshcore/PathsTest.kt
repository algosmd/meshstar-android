package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PathsTest {
    private val key=ByteArray(32){9}
    @Test fun packedPathsSupportModesAndPreserveContactMetadata(){
        val p=ContactPath.parse("aabb:1",1)
        assertEquals(2,p.width);assertEquals(0x41,p.encodedLength);assertEquals("aabb",p.text)
        val c=NodeContact(key,"Synthetic",2,7,50,255,ByteArray(64),40,-1250000,2500000)
        val round=NodeContact.parse(p.apply(c).updateCommand().also{it[0]=3})
        assertTrue(p.same(ContactPath.from(round)));assertEquals(c.flags,round.flags);assertEquals(c.advLat,round.advLat);assertEquals(c.modified,round.modified)
        assertTrue(runCatching{ContactPath.parse("aab:1",1)}.isFailure)
        assertTrue(runCatching{ContactPath(ByteArray(64),1)}.isFailure)
        assertTrue(runCatching{ContactPath(ByteArray(3),2)}.isFailure)
    }
    @Test fun discoveryParsesVariableWidthAndRejectsTruncatedOrAmbiguousPaths(){
        val b=byteArrayOf(0x8d.toByte(),0)+key.copyOf(6)+byteArrayOf(0x41,0xaa.toByte(),0xbb.toByte(),0)
        val p=DiscoveredPath.parse(b);assertEquals(2,p.outbound.width);assertEquals("aabb",p.outbound.text)
        assertTrue(runCatching{DiscoveredPath.parse(b.copyOf(b.size-1))}.isFailure)
        assertTrue(runCatching{DiscoveredPath.parse(b+0)}.isFailure)
    }
    @Test fun traceUsesLog2WidthAndRoundTripAndSignedSnr(){
        val h=listOf(byteArrayOf(1,2),byteArrayOf(3,4),byteArrayOf(1,2))
        assertArrayEquals(byteArrayOf(0x24)+little(7)+little(8)+byteArrayOf(1,1,2,3,4,1,2),TraceResult.command(h,7,8))
        val b=byteArrayOf(0x89.toByte(),0,6,1)+little(7)+little(8)+byteArrayOf(1,2,3,4,1,2,-8,12,4,-4)
        val result=TraceResult.parse(b);assertEquals(listOf(-2.0,3.0,1.0),result.snrs);assertEquals(-1.0,result.finalSnr,0.0)
        val c=ContactPath(byteArrayOf(1,2),2).apply(NodeContact(ByteArray(32){3},"Synthetic",2,0,0))
        assertEquals(3,TraceResult.roundTrip(c,emptyList()).size)
        assertTrue(runCatching{TraceResult.parse(b+0)}.isFailure)
        assertTrue(runCatching{TraceResult.roundTrip(ContactPath(byteArrayOf(1,2,3),3).apply(c),emptyList())}.isFailure)
    }
    @Test fun discoveryPersistsOutboundAndResetVerifiesReadback()=runTest{
        var contact=NodeContact(key,"Synthetic",2,0,0)
        val writes=mutableListOf<ByteArray>();lateinit var controller:PathController
        controller=PathController(send={b->writes+=b
            when(b[0].toInt() and 255){
                0x34->launch{controller.receive(byteArrayOf(0x8d.toByte(),0)+key.copyOf(6)+byteArrayOf(1,5,0))}
                9->contact=NodeContact.parse(b.copyOf().also{it[0]=3})
                0x0d->contact=contact.copy(pathLength=255)
            };byteArrayOf(6,0)+ByteArray(4)+little(1000)
        },read={contact},publish={contact=it},known={listOf(contact)},allowed={true})
        controller.open(key);controller.discover()
        assertEquals("05",ContactPath.from(contact).text);assertTrue(writes.any{it.first()==9.toByte()})
        controller.set(null);assertEquals(255,controller.state.value.contact!!.pathLength)
        val work=launch{controller.trace()};runCurrent();work.join();assertNotNull(controller.state.value.error)
        controller.close();assertNull(controller.state.value.contact)
    }
    @Test fun closeDuringDiscoveryNeverWritesLateRoute()=runTest{
        val contact=NodeContact(key,"Synthetic",2,0,0);val writes=mutableListOf<ByteArray>();lateinit var c:PathController
        c=PathController({writes+=it;byteArrayOf(6,0)+ByteArray(8)},{contact},{},{listOf(contact)},{true})
        c.open(key);val work=launch{c.discover()};runCurrent();c.close();runCurrent()
        c.receive(byteArrayOf(0x8d.toByte(),0)+key.copyOf(6)+byteArrayOf(1,5,0));work.join()
        assertFalse(writes.any{it.first()==9.toByte()});assertNull(c.state.value.contact)
    }
}
