package net.meshstar

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import net.meshstar.codec2.Codec2
import net.meshstar.voice.VoiceWav
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Codec2InstrumentedTest {
    private fun decodeHex(text: String): ByteArray = ByteArray(text.length / 2) {
        Integer.parseInt(text.substring(it * 2, it * 2 + 2), 16).toByte()
    }
    @Test fun nativeEncoderMatchesGoldenVectorAndDecodeProducesWav() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("rns_lite.json").reader().use { JSONObject(it.readText()) }
        val vector = root.getJSONObject("codec2")
        val raw = decodeHex(vector.getString("pcm16_le_hex"))
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(raw.size / 2) { buffer.short }
        val encoded = Codec2.encode(samples)
        assertArrayEquals(decodeHex(vector.getString("codec2")), encoded)
        val decoded = Codec2.decode(encoded)
        assertEquals(encoded.size / 6 * 320, decoded.size)
        assertTrue(decoded.any { it.toInt() != 0 })
        val wav = VoiceWav.encode(decoded)
        assertEquals("RIFF", String(wav,0,4,java.nio.charset.StandardCharsets.US_ASCII))
        assertEquals(44 + decoded.size*2, wav.size)
        assertArrayEquals(encoded, Codec2.encode(samples)) // Fresh state, no preceding-note contamination.
    }
    @Test fun nativeCodecRejectsMalformedAndOverlongBuffers() {
        assertTrue(runCatching { Codec2.decode(ByteArray(7)) }.isFailure)
        assertTrue(runCatching { Codec2.decode(ByteArray(0)) }.isFailure)
        assertTrue(runCatching { Codec2.encode(ShortArray(0)) }.isFailure)
        assertTrue(runCatching { Codec2.encode(ShortArray(Codec2.MAX_SAMPLES+1)) }.isFailure)
        val bytes=Codec2.encode(ShortArray(Codec2.MAX_SAMPLES){(it%100).toShort()})
        assertEquals(Codec2.MAX_BYTES,bytes.size)
        assertEquals(Codec2.MAX_SAMPLES,Codec2.decode(bytes).size)
    }
}
