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
    @Test fun matchedPushCanCompleteTelemetryButOtherNodeCannot()=runTest {
        val key=ByteArray(6){3};val q=CommandQueue({});q.open()
        val reply=async{q.command(byteArrayOf(0x27,0,0,0),setOf(0x8b),matching={it.size>=8&&it.copyOfRange(2,8).contentEquals(key)})}
        runCurrent();q.receive(byteArrayOf(0x8b.toByte(),0)+ByteArray(6));assertFalse(reply.isCompleted)
        val frame=byteArrayOf(0x8b.toByte(),0)+key;q.receive(frame);assertArrayEquals(frame,reply.await())
    }
}
