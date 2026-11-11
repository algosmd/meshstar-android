package net.meshstar.rnslite

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CancellationException

class ProtocolSafetyTest {
    private val alice = Identity(ByteArray(64) { (it + 1).toByte() })
    private val bob = Identity(ByteArray(64) { (it + 65).toByte() })
    private fun rejects(block: () -> Unit) { assertThrows(RNSError::class.java, block) }
    @Test fun authenticationBeforePlaintextAndInvalidPeer() {
        val plain = "synthetic payload".toByteArray()
        val encrypted = bob.encrypt(plain)
        for (index in listOf(0, 32, 48, encrypted.lastIndex)) {
            val changed = encrypted.copyOf(); changed[index] = (changed[index].toInt() xor 1).toByte()
            rejects { bob.decrypt(changed) }
        }
        rejects { alice.decrypt(encrypted) }
        rejects { PublicIdentity(ByteArray(64)).encrypt(plain) }
        rejects { Identity(ByteArray(63)) }
        rejects { Token(ByteArray(32)) }
        rejects { bob.decrypt(byteArrayOf(1)) }
        assertFalse(alice.validate(ByteArray(63), plain))
    }
    @Test fun packetBoundsAndProofBinding() {
        rejects { Packet(ByteArray(18)) }; rejects { Packet(ByteArray(501)) }
        rejects { Packet(byteArrayOf(0x40) + ByteArray(18)) }
        val packet = LXMessage.create(alice, bob, byteArrayOf(1), 1.0).packet(bob)
        val proof = packet.prove(bob)
        val explicit = Packet.build(Packet.PROOF, packet.truncatedHash, packet.hash + proof.payload)
        assertTrue(packet.validateProof(explicit, bob))
        val wrongHash = packet.hash; wrongHash[31] = (wrongHash[31].toInt() xor 1).toByte()
        assertFalse(packet.validateProof(Packet.build(Packet.PROOF, packet.truncatedHash, wrongHash + proof.payload), bob))
        val different = LXMessage.create(alice, bob, byteArrayOf(2), 1.0).packet(bob)
        assertFalse(different.validateProof(proof, bob))
        rejects { packet.via(ByteArray(16)).via(ByteArray(16)) }
        rejects { LXMessage.receive(packet, bob) { null } }
        rejects { LXMessage.receive(packet, bob) { bob } }
        rejects { LXMessage.receive(packet, alice) { alice } }
        val forgedPlain = alice.lxmfAddress + ByteArray(64) + MsgPack.encode(listOf(1.0, byteArrayOf(), byteArrayOf(), MsgPack.MapValue()))
        rejects { LXMessage.receive(Packet.build(Packet.DATA, bob.lxmfAddress, bob.encrypt(forgedPlain)), bob) { alice } }
    }
    @Test fun msgPackNumbersNestedFieldsAndMalformedInput() {
        val values = listOf(null, true, false, -33L, -129L, -32769L, Long.MIN_VALUE, 127L, 128L, 256L,
            65536L, Long.MAX_VALUE, ULong.MAX_VALUE, 1.5, 1.25f, "x".repeat(256), ByteArray(256) { it.toByte() },
            listOf(1L, "two"), MsgPack.MapValue(listOf(1L to byteArrayOf(3), "four" to listOf(false))))
        val encoded = MsgPack.encode(values)
        assertArrayEquals(encoded, MsgPack.encode(MsgPack.decode(encoded)))
        for (hex in listOf("c1", "c40201", "dbffffffff", "91", "81", "a1ff", "c000")) rejects { MsgPack.decode(hex.unhex()) }
        rejects { MsgPack.decode(ByteArray(40) { 0x91.toByte() } + byteArrayOf(0)) }
        val fields = MsgPack.MapValue(listOf(1L to "synthetic"))
        val m = LXMessage.create(alice, bob, byteArrayOf(1), 1.0, fields = fields)
        val received = LXMessage.receive(m.packet(bob), bob) { alice }
        assertEquals(fields, received.fields)
    }
    @Test fun fragmentLimitsExpiryAndConflicts() {
        rejects { MeshCoreFrag.parse(byteArrayOf(0, 1, 0, 0xa1.toByte(), 1)) }
        rejects { MeshCoreFrag.split(ByteArray(2386), 1) }
        rejects { MeshCoreFrag.split(byteArrayOf(), 1) }
        var time = 0L
        val assembler = MeshCoreFrag.Assembler({ time }, ttlMillis = 10, capacity = 2)
        val parts = MeshCoreFrag.split(ByteArray(200) { it.toByte() }, 1)
        assertNull(assembler.feed(parts[0])); time = 11; assertNull(assembler.feed(parts[1]))
        assertArrayEquals(ByteArray(200) { it.toByte() }, assembler.feed(parts[0]))
        assertNull(assembler.feed(parts[0]))
        val conflict = parts[0].copyOf(); conflict[4] = 99
        assertNull(assembler.feed(conflict)); assertNull(assembler.feed(parts[1]))
    }
    @Test fun textCodecRejectsCorruptionUnsupportedDictionaryAndBomb() {
        rejects { TextCodec.decode(byteArrayOf()) }
        rejects { TextCodec.decode(byteArrayOf(0, 0xff.toByte())) }
        rejects { TextCodec.decode(byteArrayOf(11, 0)) }
        rejects { TextCodec.decode(byteArrayOf(7, 1)) }
        rejects { TextCodec.decode(byteArrayOf(1, 65, 65), maxOutputBytes = 1) }
        val deflater = java.util.zip.Deflater(9, true)
        val compressed = try {
            deflater.setInput(ByteArray(10000) { 65 }); deflater.finish()
            val bytes = ByteArray(1000); bytes.copyOf(deflater.deflate(bytes))
        } finally { deflater.end() }
        rejects { TextCodec.decode(byteArrayOf(6) + compressed, maxOutputBytes = 100) }
        rejects { TextCodec.decode(byteArrayOf(6) + compressed + byteArrayOf(1)) }
    }
    @Test fun mailboxBoundsAndCancellation() {
        rejects { Mailbox.address(alice, bob, ByteArray(16), LocalDate.of(2026, 1, 1)) }
        rejects { Mailbox.publicAddress(ByteArray(15)) }
        rejects { MailboxStamp.solve(ByteArray(16), byteArrayOf(), 25) }
        assertThrows(CancellationException::class.java) { MailboxStamp.solve(ByteArray(16), byteArrayOf(), 16) { true } }
        assertEquals(256, MailboxStamp.leadingZeroBits(ByteArray(32)))
        assertEquals(15, MailboxStamp.leadingZeroBits(byteArrayOf(0, 1)))
    }
    @Test fun voiceBoundsSenderSignatureAndLastFrameProof() {
        rejects { VoiceFrame.create(alice, bob, 1, ByteArray(1199)) }
        rejects { VoiceFrame.create(alice, bob, 1, byteArrayOf(), mode = 999) }
        rejects { VoiceFrame.frames(alice, bob, 1, ByteArray(1197 * 4 + 1)) }
        rejects { VoiceFrame.create(alice, bob, 1, byteArrayOf(), part = VoiceFrame.Part(3, 3)) }
        // Even correctly signed ciphertext must not smuggle reserved flags or impossible part indices.
        for (inner in listOf("0000000135".unhex(), "000000014533".unhex())) {
            val invalid = VoiceFrame.magic + bob.lxmfAddress + alice.lxmfAddress +
                bob.encrypt(inner + alice.sign(bob.lxmfAddress + alice.lxmfAddress + inner))
            rejects { VoiceFrame.receive(invalid, bob) { alice } }
        }
        val frames = VoiceFrame.frames(alice, bob, 1, ByteArray(2500) { it.toByte() })
        val assembler = VoiceFrame.Assembler()
        assertNull(assembler.feed(frames[0])); assertNull(assembler.feed(frames[1]))
        assertArrayEquals(ByteArray(2500) { it.toByte() }, assembler.feed(frames[2])!!.codec2)
        rejects { VoiceFrame.receive(frames[0].frame, bob) { null } }
        rejects { VoiceFrame.receive(frames[0].frame, bob) { bob } }
        val corrupted = frames[0].frame.copyOf(); corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        rejects { VoiceFrame.receive(corrupted, bob) { alice } }
        val proof = VoiceFrame.proof(frames.last().frame, bob)
        assertTrue(VoiceFrame.validateProof(proof, frames.last().frame, bob))
        assertFalse(VoiceFrame.validateProof(proof, frames.first().frame, bob))
    }
}
