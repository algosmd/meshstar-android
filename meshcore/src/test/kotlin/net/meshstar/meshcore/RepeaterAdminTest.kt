package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepeaterAdminTest {
    private val key=ByteArray(32){9}
    private val contact=NodeContact(key,"Synthetic repeater",2,0,0,2)
    private fun login(success:Boolean=true,admin:Boolean=true)=byteArrayOf((if(success)0x85 else 0x86).toByte(),if(admin)1 else 0)+key.copyOf(6)
    private fun cli(text:String,time:Long=1)=byteArrayOf(7)+key.copyOf(6)+byteArrayOf(0,1)+little(time)+text.toByteArray()
    @Test fun wireCommandsAndAdaptiveWait(){
        assertArrayEquals(byteArrayOf(0x1a)+key+"demo".toByteArray(),AdminProtocol.login(key,"demo"))
        assertArrayEquals(byteArrayOf(2,1,0)+little(1)+key.copyOf(6)+"time".toByteArray(),AdminProtocol.command(key,"time",1))
        assertTrue(runCatching{AdminProtocol.login(key,"x".repeat(16))}.isFailure)
        assertTrue(runCatching{AdminProtocol.command(key,"get radio\nreboot",1)}.isFailure)
        assertEquals(60,RemoteExchange.waitSeconds(byteArrayOf(6,1)+ByteArray(8),255))
        assertEquals(125,RemoteExchange.waitSeconds(byteArrayOf(6,0)+ByteArray(4)+little(60000),1))
        assertEquals(300,RemoteExchange.waitSeconds(byteArrayOf(6),255,300))
        assertTrue(AdminProtocol.confirm("reboot"));assertFalse(AdminProtocol.confirm("get radio"))
    }
    @Test fun loginAndConsoleFilterRepliesAndPersistOnlyAuthenticatedPassword()=runTest{
        val saved=mutableListOf<String>();val sent=mutableListOf<ByteArray>()
        val admin=RepeaterAdmin(this,{sent+=it;byteArrayOf(6,0)+ByteArray(4)+little(1000)},{true},{_,pwd->saved+=pwd})
        admin.open(contact)
        val work=launch{admin.login("fixture")};runCurrent()
        admin.receive(login().also{it[2]=3});runCurrent();assertTrue(admin.state.value.busy)
        admin.receive(login());work.join();assertTrue(admin.state.value.loggedIn);assertEquals(listOf("fixture"),saved)
        val command=launch{admin.run("time")};runCurrent();assertTrue(admin.receive(cli("fixture UTC")));command.join()
        assertEquals("•••• UTC",admin.state.value.lines.last())
        val late=launch{admin.run("ver")};runCurrent();admin.close();runCurrent();late.join()
        assertTrue(admin.receive(cli("late",2)));assertTrue(admin.state.value.lines.isEmpty());assertFalse(admin.state.value.loggedIn)
        assertTrue(sent.any{it.first()==0x1d.toByte()})
    }
    @Test fun wrongPasswordReadOnlyAndTimeoutDoNotAuthenticate()=runTest{
        val saved=mutableListOf<String>()
        val admin=RepeaterAdmin(this,{byteArrayOf(6,0)+ByteArray(4)+little(1000)},{true},{_,pwd->saved+=pwd})
        admin.open(contact)
        var work=launch{admin.login("bad")};runCurrent();admin.receive(login(false));work.join()
        assertEquals(NodeError.Code.LOGIN_FAILED,admin.state.value.error);assertTrue(saved.isEmpty())
        work=launch{admin.login("guest")};runCurrent();admin.receive(login(admin=false));work.join()
        assertEquals(NodeError.Code.READ_ONLY,admin.state.value.error)
        work=launch{admin.login("bad",60)};runCurrent();advanceTimeBy(60001);runCurrent();work.join()
        assertEquals(NodeError.Code.RADIO_TIMEOUT,admin.state.value.error);assertFalse(admin.state.value.loggedIn)
        admin.close()
    }
    @Test fun statusRequiresMatchingKeyAndCompleteFrame(){
        val b=ByteArray(60);b[0]=0x87.toByte();key.copyOf(6).copyInto(b,2);b[8]=0x10;b[9]=0x0e;little(42).copyInto(b,28)
        assertEquals(3600,RepeaterStatus.parse(b,key).batteryMV);assertEquals(42L,RepeaterStatus.parse(b,key).uptime)
        assertTrue(runCatching{RepeaterStatus.parse(b.copyOf(59),key)}.isFailure)
    }
}
