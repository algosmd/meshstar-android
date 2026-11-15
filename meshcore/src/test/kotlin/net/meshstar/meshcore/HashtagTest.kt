package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HashtagTest {
    @Test fun namesAndKeysMatchStandardWithoutCaseFolding() {
        assertEquals("#demo",Hashtag.name(" demo "))
        assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest("#demo".toByteArray()).copyOf(16),Hashtag.key("demo"))
        assertFalse(Hashtag.key("demo").contentEquals(Hashtag.key("Demo")))
        assertEquals(listOf("#demo","#тест_2"),Hashtag.matches("join #demo and #тест_2; not a#word #x #"+"a".repeat(33)).map{it.value})
        assertTrue(runCatching{Hashtag.name("я".repeat(16))}.isFailure)
        assertTrue(runCatching{Hashtag.name("a\u0000b")}.isFailure)
    }
    @Test fun openExistingReplaceAndLeavePreserveProtectedSlots()= runTest {
        val slots=mutableListOf(Channel(0,"Public",ByteArray(16){1}),Channel(1,"MESHSAT",ByteArray(16){2}),Channel(2,"",ByteArray(16)),Channel(3,"#old",Hashtag.key("old")))
        lateinit var s:Session
        var writes=0
        s=Session(this,write={f->launch{
            when(f[0].toInt() and 255){
                1->s.queue.receive(ByteArray(58).also{it[0]=5})
                0x16->s.queue.receive(byteArrayOf(0x0d,3,0,4,0,0,0,0))
                0x1f->{val c=slots[f[1].toInt()];s.queue.receive(byteArrayOf(0x12,c.index.toByte())+c.name.toByteArray().copyOf(32)+c.secret)}
                0x20->{writes++;slots[f[1].toInt()]=Channel(f[1].toInt(),f.copyOfRange(2,34).takeWhile{it!=0.toByte()}.toByteArray().toString(Charsets.UTF_8),f.copyOfRange(34,50));s.queue.receive(byteArrayOf(0))}
                4->{s.queue.receive(byteArrayOf(2)+little(0));s.queue.receive(byteArrayOf(4)+little(0))}
                10->s.queue.receive(byteArrayOf(10))
            }
        }},disconnect={},save={},packet={_,_->},foreign={})
        s.initialize(NodeConfiguration("fixture","fixture",1));runCurrent()
        assertEquals("#demo",s.openHashtag("demo"));assertEquals(1,writes)
        s.openHashtag("demo");assertEquals(1,writes)
        assertTrue(runCatching{s.openHashtag("other",1,slots[1].fingerprint)}.isFailure)
        assertTrue(runCatching{s.openHashtag("other",0,slots[0].fingerprint)}.isFailure)
        val stale=slots[3].fingerprint
        slots[3]=Channel(3,"#changed",Hashtag.key("changed"))
        assertTrue(runCatching{s.openHashtag("other",3,stale)}.isFailure)
        s.openHashtag("other",3,slots[3].fingerprint);assertEquals("#other",slots[3].name)
        s.leaveChannel(3,slots[3].fingerprint);assertTrue(slots[3].empty)
        assertEquals("Public",slots[0].name);assertEquals("MESHSAT",slots[1].name)
        assertTrue(s.state.value.channels.all{it.secret.isEmpty()});s.stop()
    }
}
