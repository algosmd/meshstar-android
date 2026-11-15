package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioSettingsTest {
    @Test fun presetAirtimeMatchesIosAndRadioValidation(){
        val radio=RadioPreset.all.first().radio
        assertEquals(976.5625,radio.bitRate,0.00001)
        assertEquals(1.885184,radio.airtime(),0.000001)
        assertArrayEquals(byteArrayOf(0x0b)+little(869618)+little(62500)+byteArrayOf(8,8,1),radio.command(true))
        assertFalse(radio.copy(cr=4).valid);assertFalse(radio.copy(sf=13).valid)
        assertFalse(radio.copy(frequency=Double.NaN).valid)
        assertFalse(radio.copy(bandwidth=99.0).valid)
    }
    @Test fun policyPreservesOtherFlagsAndCoordinatesAreSigned(){
        val snapshot=NodeSettings("fixture",ByteArray(32),RadioPreset.all[0].radio,22,22,0.0,0.0,0,3,1,2)
        assertArrayEquals(byteArrayOf(0x26,1,3,1,2),NodeChange.AdvertPolicy(1).frame(snapshot))
        val frame=NodeChange.Coordinates(-1.25,2.5).frame(snapshot)
        assertEquals(-1250000,frame.word(1).toInt());assertEquals(2500000,frame.word(5).toInt())
        assertTrue(runCatching{NodeChange.Power(23).frame(snapshot)}.isFailure)
        assertTrue(runCatching{NodeChange.Name("a\nb").frame(snapshot)}.isFailure)
        assertTrue(runCatching{NodeChange.Coordinates(91.0,0.0).frame(snapshot)}.isFailure)
        assertEquals(50,snapshot.copy(batteryMV=3600).batteryPercent)
        assertNull(snapshot.copy(batteryMV=5000).batteryPercent)
    }
    @Test fun telemetryChecksSenderAndNeverDecodesGps(){
        val key=ByteArray(32){5};val b=byteArrayOf(0x8b.toByte(),0)+key.copyOf(6)+byteArrayOf(1,116,1,104,2,103,0,(-50).toByte(),3,(-120).toByte())+ByteArray(9)
        assertEquals(mapOf("V" to 3.6,"°C" to 20.6),LocalTelemetry.parse(b,key))
        assertTrue(runCatching{LocalTelemetry.parse(b,ByteArray(32))}.isFailure)
        assertTrue(runCatching{LocalTelemetry.parse(byteArrayOf(0x8b.toByte(),0)+key.copyOf(6)+byteArrayOf(0,116,1),key)}.isFailure)
    }
    @Test fun radioChangeWritesSelectedCrAndRequiresReadback()=runTest {
        var radio=RadioPreset.all[0].radio.copy(cr=5)
        var acceptWrite=true
        val writes=mutableListOf<ByteArray>()
        lateinit var session:Session
        fun snapshot()=ByteArray(58).also{b->
            b[0]=5;b[2]=22;b[3]=22
            little((radio.frequency*1000).toLong()).copyInto(b,48)
            little((radio.bandwidth*1000).toLong()).copyInto(b,52)
            b[56]=radio.sf.toByte();b[57]=radio.cr.toByte()
        }
        session=Session(this,write={f->launch{
            when(f.u(0)){
                1->session.queue.receive(snapshot())
                0x16->session.queue.receive(byteArrayOf(0x0d,3,0,1,0,0,0,0))
                0x1f->session.queue.receive(byteArrayOf(0x12,0)+"MESHSAT".toByteArray().copyOf(32)+ByteArray(16){1})
                4->{session.queue.receive(byteArrayOf(2)+little(0));session.queue.receive(byteArrayOf(4)+little(0))}
                10->session.queue.receive(byteArrayOf(10))
                0x0b->{writes.add(f);if(acceptWrite)radio=RadioParameters(f.word(1)/1000.0,f.word(5)/1000.0,f.u(9),f.u(10));session.queue.receive(byteArrayOf(0))}
            }
        }},disconnect={},save={},packet={_,_->},foreign={})
        session.initialize(NodeConfiguration("fixture","fixture",0));runCurrent()
        val desired=radio.copy(cr=8)
        session.changeSettings(NodeChange.Radio(desired))
        assertEquals(desired,session.state.value.settings!!.radio)
        assertEquals(8,writes.single().u(10))
        assertEquals(22,session.state.value.settings!!.power)
        session.changeSettings(NodeChange.Radio(desired.copy(cr=5)))
        assertEquals(5,session.state.value.settings!!.radio.cr)
        acceptWrite=false
        assertTrue(runCatching{session.changeSettings(NodeChange.Radio(desired))}.isFailure)
        assertEquals(5,session.state.value.settings!!.radio.cr)
        session.stop()
    }
    @Test fun matchedPushCanCompleteTelemetryButOtherNodeCannot()=runTest {
        val key=ByteArray(6){3};val q=CommandQueue({});q.open()
        val reply=async{q.command(byteArrayOf(0x27,0,0,0),setOf(0x8b),matching={it.size>=8&&it.copyOfRange(2,8).contentEquals(key)})}
        runCurrent();q.receive(byteArrayOf(0x8b.toByte(),0)+ByteArray(6));assertFalse(reply.isCompleted)
        val frame=byteArrayOf(0x8b.toByte(),0)+key;q.receive(frame);assertArrayEquals(frame,reply.await())
    }
}
